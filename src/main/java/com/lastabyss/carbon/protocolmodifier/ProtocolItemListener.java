package com.lastabyss.carbon.protocolmodifier;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.lastabyss.carbon.Carbon;
import com.lastabyss.carbon.utils.Utilities;

import net.minecraft.server.v1_7_R4.Item;
import net.minecraft.server.v1_7_R4.PacketDataSerializer;
import net.minecraft.server.v1_7_R4.WatchableObject;
import net.minecraft.util.io.netty.buffer.Unpooled;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class ProtocolItemListener {

	private Carbon plugin;

	public ProtocolItemListener(Carbon plugin) {
		this.plugin = plugin;
	}

	private int[] replacements = new int[4096];
	{
		for (int i = 0; i < replacements.length; i++) {
			replacements[i] = -1;
		}
		//slime -> emerald block
		replacements[165] = plugin.getConfig().getInt("protocollib.items.slime");
		//barrier -> bedrock
		replacements[166] = plugin.getConfig().getInt("protocollib.items.barrier");
		//iron trapdoor -> trapdoor
		replacements[167] = plugin.getConfig().getInt("protocollib.items.iron_trapdoor");
		//prismarine -> mossy cobblestone
		replacements[168] = plugin.getConfig().getInt("protocollib.items.prismarine");
		//sea lantern -> glowstone
		replacements[169] = plugin.getConfig().getInt("protocollib.items.sea_lantern");
		//red sandstone -> sandstone
		replacements[179] = plugin.getConfig().getInt("protocollib.items.red_sandstone");
		//red sandstone stairs -> sandstone stairs
		replacements[180] = plugin.getConfig().getInt("protocollib.items.red_sandstone_stairs");
		//red sandstone doubleslab -> double step
		replacements[181] = plugin.getConfig().getInt("protocollib.items.red_sandstone_doubleslab");
		//red sandstone slab -> step
		replacements[182] = plugin.getConfig().getInt("protocollib.items.red_sandstone_slab");
		//all fence gates -> fence gate
		replacements[183] = plugin.getConfig().getInt("protocollib.items.fence_gates");
		replacements[184] = plugin.getConfig().getInt("protocollib.items.fence_gates");
		replacements[185] = plugin.getConfig().getInt("protocollib.items.fence_gates");
		replacements[186] = plugin.getConfig().getInt("protocollib.items.fence_gates");
		replacements[187] = plugin.getConfig().getInt("protocollib.items.fence_gates");
		//all fences -> fence
		replacements[188] = plugin.getConfig().getInt("protocollib.items.fences");
		replacements[189] = plugin.getConfig().getInt("protocollib.items.fences");
		replacements[190] = plugin.getConfig().getInt("protocollib.items.fences");
		replacements[191] = plugin.getConfig().getInt("protocollib.items.fences");
		replacements[192] = plugin.getConfig().getInt("protocollib.items.fences");
		//all doors -> door
		replacements[427] = plugin.getConfig().getInt("protocollib.items.doors");
		replacements[428] = plugin.getConfig().getInt("protocollib.items.doors");
		replacements[429] = plugin.getConfig().getInt("protocollib.items.doors");
		replacements[430] = plugin.getConfig().getInt("protocollib.items.doors");
		replacements[431] = plugin.getConfig().getInt("protocollib.items.doors");
		//rabbit raw meat -> chicken raw meat
		replacements[411] = plugin.getConfig().getInt("protocollib.items.rabbit_meat");
		//rabbit cooked meat -> chicken cooked meat
		replacements[412] = plugin.getConfig().getInt("protocollib.items.rabbit_cooked_meat");
		//rabbit stew -> mushroom stew
		replacements[413] = plugin.getConfig().getInt("protocollib.items.rabbit_stew");
		//raw mutton -> chicken raw meat
		replacements[423] = plugin.getConfig().getInt("protocollib.items.mutton");
		//cooked mutton -> chicken cooked meat
		replacements[424] = plugin.getConfig().getInt("protocollib.items.cooked_mutton");
		//banner -> sign
		replacements[425] = plugin.getConfig().getInt("protocollib.items.banner");
		//everything else -> stone
		replacements[409] = plugin.getConfig().getInt("protocollib.items.prismarine_shard");
		replacements[410] = plugin.getConfig().getInt("protocollib.items.prismarine_crystals");
		replacements[414] = plugin.getConfig().getInt("protocollib.items.rabbit_foot");
		replacements[415] = plugin.getConfig().getInt("protocollib.items.rabbit_hide");
		replacements[416] = plugin.getConfig().getInt("protocollib.items.armor_stand");
	}

	@SuppressWarnings("deprecation")
	private void replaceItemStack(ItemStack itemStack) {
		if (itemStack == null) {
			return;
		}
		int itemid = itemStack.getTypeId();
		if (replacements[itemid] != -1) {
			itemStack.setTypeId(replacements[itemid]);
		}
	}

	private void replaceItemStack(net.minecraft.server.v1_7_R4.ItemStack itemStack) {
		if (itemStack == null) {
			return;
		}
		int itemid = Item.getId(itemStack.getItem());
		if (replacements[itemid] != -1) {
			itemStack.setItem(Item.getById(replacements[itemid]));
		}
	}

	public void init() {
		ProtocolLibrary.getProtocolManager().addPacketListener(
			new PacketAdapter(
				PacketAdapter
				.params(plugin, PacketType.Play.Server.WINDOW_ITEMS)
				.listenerPriority(ListenerPriority.HIGHEST)
			) {
				@Override
				public void onPacketSending(PacketEvent event) {
					if (Utilities.getProtocolVersion(event.getPlayer()) == Utilities.CLIENT_1_8_PROTOCOL_VERSION) {
						return;
					}
					//replace all items with valid ones
					ItemStack[] items = event.getPacket().getItemArrayModifier().read(0);
					for (int i = 0; i < items.length; i++) {
						replaceItemStack(items[i]);
					}
				}
			}
		);

		ProtocolLibrary.getProtocolManager().addPacketListener(
			new PacketAdapter(
				PacketAdapter
				.params(plugin, PacketType.Play.Server.SET_SLOT)
				.listenerPriority(ListenerPriority.HIGHEST)
			) {
				@Override
				public void onPacketSending(PacketEvent event) {
					if (Utilities.getProtocolVersion(event.getPlayer()) == Utilities.CLIENT_1_8_PROTOCOL_VERSION) {
						return;
					}
					//replace item with valid one
					ItemStack item = event.getPacket().getItemModifier().read(0);
					replaceItemStack(item);
				}
			}
		);

		ProtocolLibrary.getProtocolManager().addPacketListener(
			new PacketAdapter(
				PacketAdapter
				.params(plugin, PacketType.Play.Server.ENTITY_EQUIPMENT)
				.listenerPriority(ListenerPriority.HIGHEST)
			) {
				@Override
				public void onPacketSending(PacketEvent event) {
					if (Utilities.getProtocolVersion(event.getPlayer()) == Utilities.CLIENT_1_8_PROTOCOL_VERSION) {
						return;
					}
					//replace item valid one
					ItemStack item = event.getPacket().getItemModifier().read(0);
					replaceItemStack(item);
				}
			}
		);

		ProtocolLibrary.getProtocolManager().addPacketListener(			
			new PacketAdapter(
				PacketAdapter
				.params(plugin, PacketType.Play.Server.ENTITY_METADATA)
				.listenerPriority(ListenerPriority.HIGHEST)
			) {
				@Override
				public void onPacketSending(PacketEvent event) {
					if (Utilities.getProtocolVersion(event.getPlayer()) == Utilities.CLIENT_1_8_PROTOCOL_VERSION) {
						return;
					}
					//create a new packet with valid items and send it (Had to do this because metadata packets are shared)
					event.setCancelled(true);
					PacketContainer newpacket = event.getPacket().deepClone();
					List<?> list = newpacket.getSpecificModifier(List.class).read(0);
					for (Object object : list) {
						WatchableObject wobject = (WatchableObject) object;
						if (wobject.b() instanceof net.minecraft.server.v1_7_R4.ItemStack) {
							net.minecraft.server.v1_7_R4.ItemStack itemStack = (net.minecraft.server.v1_7_R4.ItemStack) wobject.b();
							replaceItemStack(itemStack);
						}
					}
					try {
						ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), newpacket, false);
					} catch (InvocationTargetException e) {
                                          e.printStackTrace();
					}
				}
			}
		);

		ProtocolLibrary.getProtocolManager().addPacketListener(			
			new PacketAdapter(
				PacketAdapter
				.params(plugin, PacketType.Play.Server.CUSTOM_PAYLOAD)
				.listenerPriority(ListenerPriority.HIGHEST)
			) {
				@Override
				public void onPacketSending(PacketEvent event) {
					//server sends some sort of payload packet on player join so this check should be first
					if (!event.getPacket().getStrings().read(0).equals("MC|TrList")) {
						return;
					}
					if (Utilities.getProtocolVersion(event.getPlayer()) == Utilities.CLIENT_1_8_PROTOCOL_VERSION) {
						return;
					}
					//repack trade list packet with valid items
					byte[] data = event.getPacket().getByteArrays().read(0);
					PacketDataSerializer dataserializer = new PacketDataSerializer(Unpooled.wrappedBuffer(data));
					PacketDataSerializer newdataserializer = new PacketDataSerializer(Unpooled.buffer(data.length));
					try {
						newdataserializer.writeInt(dataserializer.readInt());
						int count = dataserializer.readByte() & 0xFF;
						newdataserializer.writeByte(count);
						for (int i = 0; i < count; i++) {
							net.minecraft.server.v1_7_R4.ItemStack buyItem1 = dataserializer.c();
							replaceItemStack(buyItem1);
							newdataserializer.a(buyItem1);

							net.minecraft.server.v1_7_R4.ItemStack buyItem3 = dataserializer.c();
							replaceItemStack(buyItem3);
							newdataserializer.a(buyItem3);

							boolean hasItem = dataserializer.readBoolean();
							newdataserializer.writeBoolean(hasItem);
							if (hasItem) {
								net.minecraft.server.v1_7_R4.ItemStack buyItem2 = dataserializer.c();
								replaceItemStack(buyItem2);
								newdataserializer.a(buyItem2);
							}

							newdataserializer.writeBoolean(dataserializer.readBoolean());
						}
						event.getPacket().getByteArrays().write(0, newdataserializer.array());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		);

	}

}
