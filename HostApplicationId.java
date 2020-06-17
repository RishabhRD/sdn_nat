package net.floodlightcontroller.sdn_nat;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

public class HostApplicationId{
	private IPv4Address address;
	private Integer port;
	private MacAddress mac;
	public HostApplicationId(IPv4Address address,Integer port){
		this.address = address;
		this.port = port;
	}
	public void setIP(IPv4Address addr){
		this.address = addr;
	}
	public void setPort(Integer port){
		this.port = port;
	}
	public IPv4Address getIP(){
		return address;
	}
	public Integer getPort(){
		return port;
	}
	public void setMac(MacAddress mac){
		this.mac = mac;
	}
	public MacAddress getMac(){
		return mac;
	}
	@Override
	public boolean equals(Object ob){
		if(this == ob){
			return true;
		}
		if(ob instanceof HostApplicationId){
			HostApplicationId id = (HostApplicationId) ob;
			return (this.address.equals(id.address) && this.port.equals(id.port));
		}
		return false;
	}
}
