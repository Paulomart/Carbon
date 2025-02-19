/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package sun.jvmstat.perfdata.monitor.protocol.rmi;

import sun.jvmstat.monitor.*;
import sun.jvmstat.monitor.event.*;
import sun.jvmstat.monitor.remote.*;
import sun.jvmstat.perfdata.monitor.*;

import java.util.*;
import java.net.*;
import java.rmi.*;

/**
 * Concrete implementation of the MonitoredHost interface for the <em>rmi</em> protocol of the HotSpot PerfData monitoring implementation.
 *
 * @author Brian Doherty
 * @since 1.5
 */
public class MonitoredHostProvider extends MonitoredHost {
	private static final String serverName = "/JStatRemoteHost";
	private static final int DEFAULT_POLLING_INTERVAL = 1000;

	private ArrayList<HostListener> listeners;
	private NotifierTask task;
	private HashSet<Integer> activeVms;
	private RemoteVmManager vmManager;
	private RemoteHost remoteHost;
	private Timer timer;

	/**
	 * Create a MonitoredHostProvider instance using the given HostIdentifier.
	 *
	 * @param hostId
	 *            the host identifier for this MonitoredHost
	 * @throws MonitorException
	 *             Thrown on any error encountered while communicating with the remote host.
	 */
	public MonitoredHostProvider(HostIdentifier hostId) throws MonitorException {
		this.hostId = hostId;
		this.listeners = new ArrayList<HostListener>();
		this.interval = DEFAULT_POLLING_INTERVAL;
		this.activeVms = new HashSet<Integer>();

		String rmiName;
		String sn = serverName;
		String path = hostId.getPath();

		if ((path != null) && (path.length() > 0)) {
			sn = path;
		}

		if (hostId.getPort() != -1) {
			rmiName = "rmi://" + hostId.getHost() + ":" + hostId.getPort() + sn;
		} else {
			rmiName = "rmi://" + hostId.getHost() + sn;
		}

		try {
			remoteHost = (RemoteHost) Naming.lookup(rmiName);

		} catch (RemoteException e) {
			/*
			 * rmi registry not available
			 * 
			 * Access control exceptions, where the rmi server refuses a connection based on policy file configuration, come through here on the client side. Unfortunately, the RemoteException doesn't contain enough information to determine the true cause of the exception. So, we have to output a rather generic message.
			 */
			String message = "RMI Registry not available at " + hostId.getHost();

			if (hostId.getPort() == -1) {
				message = message + ":" + java.rmi.registry.Registry.REGISTRY_PORT;
			} else {
				message = message + ":" + hostId.getPort();
			}

			if (e.getMessage() != null) {
				throw new MonitorException(message + "\n" + e.getMessage(), e);
			} else {
				throw new MonitorException(message, e);
			}

		} catch (NotBoundException e) {
			// no server with given name
			String message = e.getMessage();
			if (message == null)
				message = rmiName;
			throw new MonitorException("RMI Server " + message + " not available", e);
		} catch (MalformedURLException e) {
			// this is a programming problem
			e.printStackTrace();
			throw new IllegalArgumentException("Malformed URL: " + rmiName);
		}
		this.vmManager = new RemoteVmManager(remoteHost);
		this.timer = new Timer(true);
	}

	/**
	 * {@inheritDoc}
	 */
	public MonitoredVm getMonitoredVm(VmIdentifier vmid) throws MonitorException {
		return getMonitoredVm(vmid, DEFAULT_POLLING_INTERVAL);
	}

	/**
	 * {@inheritDoc}
	 */
	public MonitoredVm getMonitoredVm(VmIdentifier vmid, int interval) throws MonitorException {
		VmIdentifier nvmid = null;
		try {
			nvmid = hostId.resolve(vmid);
			RemoteVm rvm = remoteHost.attachVm(vmid.getLocalVmId(), vmid.getMode());
			RemoteMonitoredVm rmvm = new RemoteMonitoredVm(rvm, nvmid, timer, interval);
			rmvm.attach();
			return rmvm;

		} catch (RemoteException e) {
			throw new MonitorException("Remote Exception attaching to " + nvmid.toString(), e);
		} catch (URISyntaxException e) {
			/*
			 * the VmIdentifier is expected to be a valid and should resolve easonably against the host identifier. A URISyntaxException here is most likely a programming error.
			 */
			throw new IllegalArgumentException("Malformed URI: " + vmid.toString(), e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void detach(MonitoredVm vm) throws MonitorException {
		RemoteMonitoredVm rmvm = (RemoteMonitoredVm) vm;
		rmvm.detach();
		try {
			remoteHost.detachVm(rmvm.getRemoteVm());

		} catch (RemoteException e) {
			throw new MonitorException("Remote Exception detaching from " + vm.getVmIdentifier().toString(), e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void addHostListener(HostListener listener) {
		synchronized (listeners) {
			listeners.add(listener);
			if (task == null) {
				task = new NotifierTask();
				timer.schedule(task, 0, interval);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void removeHostListener(HostListener listener) {
		/*
		 * XXX: if a disconnect method is added, make sure it calls this method to unregister this object from the watcher. otherwise, an unused MonitoredHostProvider instance may go uncollected.
		 */
		synchronized (listeners) {
			listeners.remove(listener);
			if (listeners.isEmpty() && (task != null)) {
				task.cancel();
				task = null;
			}
		}
	}

	public void setInterval(int newInterval) {
		synchronized (listeners) {
			if (newInterval == interval) {
				return;
			}

			int oldInterval = interval;
			super.setInterval(newInterval);

			if (task != null) {
				task.cancel();
				NotifierTask oldTask = task;
				task = new NotifierTask();
				CountedTimerTaskUtils.reschedule(timer, oldTask, task, oldInterval, newInterval);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public Set<Integer> activeVms() throws MonitorException {
		return vmManager.activeVms();
	}

	/**
	 * Fire VmStatusChangeEvent events to HostListener objects
	 *
	 * @param active
	 *            Set of Integer objects containing the local Vm Identifiers of the active JVMs
	 * @param started
	 *            Set of Integer objects containing the local Vm Identifiers of new JVMs started since last interval.
	 * @param terminated
	 *            Set of Integer objects containing the local Vm Identifiers of terminated JVMs since last interval.
	 */
	@SuppressWarnings("unchecked")
	private void fireVmStatusChangedEvents(Set<Integer> active, Set<Integer> started, Set<Object> terminated) {
		ArrayList<HostListener> registered = null;
		VmStatusChangeEvent ev = null;

		synchronized (listeners) {
			registered = (ArrayList<HostListener>) listeners.clone();
		}

		for (Iterator<HostListener> i = registered.iterator(); i.hasNext(); /* empty */) {
			HostListener l = (HostListener) i.next();
			if (ev == null) {
				ev = new VmStatusChangeEvent(this, active, started, terminated);
			}
			l.vmStatusChanged(ev);
		}
	}

	/**
	 * Fire hostDisconnectEvent events.
	 */
	@SuppressWarnings("unchecked")
	void fireDisconnectedEvents() {
		ArrayList<HostListener> registered = null;
		HostEvent ev = null;

		synchronized (listeners) {
			registered = (ArrayList<HostListener>) listeners.clone();
		}

		for (Iterator<HostListener> i = registered.iterator(); i.hasNext(); /* empty */) {
			HostListener l = (HostListener) i.next();
			if (ev == null) {
				ev = new HostEvent(this);
			}
			l.disconnected(ev);
		}
	}

	/**
	 * class to poll the remote machine and generate local event notifications.
	 */
	private class NotifierTask extends CountedTimerTask {
		public void run() {
			super.run();

			// save the last set of active JVMs
			Set<Integer> lastActiveVms = activeVms;

			try {
				// get the current set of active JVMs
				activeVms = (HashSet<Integer>) vmManager.activeVms();

			} catch (MonitorException e) {
				// XXX: use logging api
				System.err.println("MonitoredHostProvider: polling task " + "caught MonitorException:");
				e.printStackTrace();

				// mark the HostManager as errored and notify listeners
				setLastException(e);
				fireDisconnectedEvents();
			}

			if (activeVms.isEmpty()) {
				return;
			}

			Set<Integer> startedVms = new HashSet<Integer>();
			Set<Object> terminatedVms = new HashSet<Object>();

			for (Iterator<Integer> i = activeVms.iterator(); i.hasNext(); /* empty */) {
				Integer vmid = (Integer) i.next();
				if (!lastActiveVms.contains(vmid)) {
					// a new file has been detected, add to set
					startedVms.add(vmid);
				}
			}

			for (Iterator<Integer> i = lastActiveVms.iterator(); i.hasNext();
			/* empty */) {
				Object o = i.next();
				if (!activeVms.contains(o)) {
					// JVM has terminated, remove it from the active list
					terminatedVms.add(o);
				}
			}

			if (!startedVms.isEmpty() || !terminatedVms.isEmpty()) {
				fireVmStatusChangedEvents(activeVms, startedVms, terminatedVms);
			}
		}
	}
}
