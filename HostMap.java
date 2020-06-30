package net.floodlightcontroller.sdn_nat;

import java.net.InetAddress;
import java.util.HashMap;

import org.projectfloodlight.openflow.types.MacAddress;

public class HostMap{
	private HashMap<Integer,HostApplicationId> map;

	public HostMap(){
		map = new HashMap<>();
	}

	public void addMapping(Integer port,MacAddress addr,InetAddress ip,Integer transportPort){
		HostApplicationId id = new HostApplicationId(ip,addr,transportPort);
		map.put(port,id);
	}

	public void removeMapping(Integer port){
		map.remove(port);
	}

	public boolean updateMapping(Integer port,InetAddress ip,Integer transportPort){
		if(!map.containsKey(port)) return false;
		HostApplicationId id = map.get(port);
		id.setPort(transportPort);
		id.setIP(ip);
		return true;
	}

	public Integer getMappedPort(Integer port){
		if(!map.containsKey(port)) return null;
		return map.get(port).getPort();
	}

	public InetAddress getMappedIP(Integer port){
		if(!map.containsKey(port)) return null;
		return map.get(port).getIP();
	}
	public MacAddress getMappedMac(Integer port){
		if(!map.containsKey(port)) return null;
		return map.get(port).getMac();
	}
	public boolean portExists(Integer port){
		return map.containsKey(port);
	}
}

