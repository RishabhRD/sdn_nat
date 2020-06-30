package net.floodlightcontroller.sdn_nat;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;

public class ARPProxy{
	private NetworkProperty nwp;
	public ARPProxy(NetworkProperty nwp){
		this.nwp = nwp;
	}
	public Command handlePacketIn(IOFSwitch sw,ARP arp,OFPort inPort){
		if(!arp.getOpCode().equals(ARP.OP_REQUEST)){
			return Command.CONTINUE;
		}
		if(!arp.getTargetProtocolAddress().equals(nwp.getGatewayIPv4Address())){
			return Command.CONTINUE;
		}
		Ethernet ethernet = new Ethernet();
		ethernet.setSourceMACAddress(nwp.getGatewayMac());
		ethernet.setDestinationMACAddress(arp.getSenderHardwareAddress());
		ethernet.setEtherType(EthType.ARP);
		ARP replyArp = new ARP();
		replyArp.setOpCode(ARP.OP_REPLY);
		replyArp.setHardwareType(ARP.HW_TYPE_ETHERNET);
		replyArp.setProtocolType(ARP.PROTO_TYPE_IP);
		replyArp.setSenderHardwareAddress(nwp.getGatewayMac());
		replyArp.setSenderProtocolAddress(nwp.getGatewayIPv4Address());
		replyArp.setTargetHardwareAddress(arp.getSenderHardwareAddress());
		replyArp.setTargetProtocolAddress(arp.getSenderProtocolAddress());
		replyArp.setHardwareAddressLength((byte)6);
		replyArp.setProtocolAddressLength((byte)4);
		ethernet.setPayload(replyArp);
		OFPacketOut.Builder ofp = sw.getOFFactory().buildPacketOut();
		ofp.setBufferId(OFBufferId.NO_BUFFER);
		ofp.setData(ethernet.serialize());
		List<OFAction> actions = new ArrayList<>();
		actions.add(sw.getOFFactory().actions().buildOutput()
				.setPort(inPort).setMaxLen(0xffFFffFF).build());
		ofp.setActions(actions);
		ofp.setInPort(OFPort.CONTROLLER);
		sw.write(ofp.build());
		return Command.STOP;
	}
}
