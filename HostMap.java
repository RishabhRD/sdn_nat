package net.floodlightcontroller.sdn_nat;

import java.util.HashMap;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

public class HostMap{
	private HashMap<Integer,HostApplicationId> map;

	public HostMap(){
		map = new HashMap<>();
	}

	public void addMapping(Integer port,MacAddress addr,IPv4Address ip,Integer transportPort){
		HostApplicationId id = new HostApplicationId(ip,addr,transportPort);
		map.put(port,id);
	}

	public void removeMapping(Integer port){
		map.remove(port);
	}

	public boolean updateMapping(Integer port,IPv4Address ip,Integer transportPort){
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

	public IPv4Address getMappedIP(Integer port){
		if(!map.containsKey(port)) return null;
		return map.get(port).getIP();
	}
	public MacAddress getMappedMac(Integer port){
		if(!map.containsKey(port)) return null;
		return map.get(port).getMac();
	}
}

