package net.floodlightcontroller.sdn_nat;

import java.net.InetAddress;

import org.projectfloodlight.openflow.types.MacAddress;

public class HostApplicationId{
	private InetAddress address;
	private Integer port;
	private MacAddress mac;
	public HostApplicationId(InetAddress address,MacAddress mac,Integer port){
		this.address = address;
		this.port = port;
		this.mac = mac;
	}
	public void setIP(InetAddress addr){
		this.address = addr;
	}
	public void setPort(Integer port){
		this.port = port;
	}
	public InetAddress getIP(){
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
