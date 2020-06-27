package net.floodlightcontroller.sdn_nat;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;

import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;

public class ARPProxy{
	private MacAddress gatewayMac;
	private IPv4Address privateGatewayIP;
	private Logger logger;
	public ARPProxy(MacAddress mac, IPv4Address addr,Logger logger){
		this.gatewayMac = mac;
		this.privateGatewayIP = addr;
		this.logger = logger;
	}
	public MacAddress getGatewayMac(){
		return gatewayMac;
	}
	public IPv4Address getGatewayIP(){
		return privateGatewayIP;
	}
	public Command handlePacketIn(IOFSwitch sw,ARP arp,OFPort inPort){
		if(!arp.getOpCode().equals(ARP.OP_REQUEST)){
			logger.info("REQ {}",arp.getTargetProtocolAddress());
			return Command.CONTINUE;
		}
		if(!arp.getTargetProtocolAddress().equals(privateGatewayIP)){
			logger.info("OTHER {}",arp.getTargetProtocolAddress());
			return Command.CONTINUE;
		}
		logger.info("HERE {}",arp.getTargetProtocolAddress());
		Ethernet ethernet = new Ethernet();
		ethernet.setSourceMACAddress(gatewayMac);
		ethernet.setDestinationMACAddress(arp.getSenderHardwareAddress());
		ethernet.setEtherType(EthType.ARP);
		ARP replyArp = new ARP();
		replyArp.setOpCode(ARP.OP_REPLY);
		replyArp.setHardwareType(ARP.HW_TYPE_ETHERNET);
		replyArp.setProtocolType(ARP.PROTO_TYPE_IP);
		replyArp.setSenderHardwareAddress(gatewayMac);
		replyArp.setSenderProtocolAddress(privateGatewayIP);
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
		if(!sw.write(ofp.build())){
			logger.info("OMG! switch writing failed");
		}
		return Command.STOP;
	}
}
