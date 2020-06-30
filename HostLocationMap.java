package net.floodlightcontroller.sdn_nat;

import java.net.InetAddress;
import java.util.HashMap;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

public class HostLocationMap{
	private HashMap<DatapathId, HashMap<InetAddress,OFPort>> locationMap;
	public HostLocationMap(){
		locationMap = new HashMap<>();
	}
	public boolean addLocation(DatapathId dpid,InetAddress addr,OFPort port){
		HashMap<InetAddress,OFPort> internalMap  = locationMap.get(dpid);
		if(internalMap == null){
			internalMap = new HashMap<>();
			locationMap.put(dpid,internalMap);
		}
		if(internalMap.containsKey(addr)) return false;
		internalMap.put(addr,port);
		return true;
	}
	public boolean removeLocation(DatapathId dpid,InetAddress addr){
		HashMap<InetAddress,OFPort> internalMap = locationMap.get(dpid);
		if(internalMap == null) return false;
		if(internalMap.containsKey(addr)){
			internalMap.remove(addr);
			return true;
		}
		return false;
	}
	public void removeIPFromAll(InetAddress addr){
		for(DatapathId dpid : locationMap.keySet()){
			HashMap<InetAddress,OFPort> internalMap = locationMap.get(dpid);
			if(internalMap.containsKey(addr)){
				internalMap.remove(addr);
			}
		}
	}
	public boolean containsSwitch(DatapathId id){
		return locationMap.containsKey(id);
	}
	public boolean containsIP(DatapathId dpid,InetAddress addr){
		HashMap<InetAddress,OFPort> internalMap = locationMap.get(dpid);
		if(internalMap == null) return false;
		return internalMap.containsKey(addr);
	}
	public void removeSwitch(DatapathId id){
		if(locationMap.containsKey(id)) 
			locationMap.remove(id);
	}
	public OFPort getLocation(DatapathId dpid,InetAddress addr){
		HashMap<InetAddress,OFPort> internalMap = locationMap.get(dpid);
		if(internalMap == null) return null;
		return internalMap.get(addr);
	}
}
