package net.floodlightcontroller.sdn_nat;

import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;

public class ARPProxy{
	private MacAddress gatewayMac;
	public ARPProxy(MacAddress mac){
		this.gatewayMac = mac;
	}
	public MacAddress getGatewayMac(){
		return gatewayMac;
	}
	public Command handlePacketIn(IOFSwitch sw,Ethernet eth,OFPort inPort){
		return Command.STOP;
	}
}
