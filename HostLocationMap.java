package net.floodlightcontroller.sdn_nat;

import java.util.HashMap;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFPort;

public class HostLocationMap{
	private HashMap<DatapathId, HashMap<IPv4Address,OFPort>> locationMap;
	public HostLocationMap(){
		locationMap = new HashMap<>();
	}
	public boolean addLocation(DatapathId dpid,IPv4Address addr,OFPort port){
		HashMap<IPv4Address,OFPort> internalMap  = locationMap.get(dpid);
		if(internalMap == null){
			internalMap = new HashMap<>();
			locationMap.put(dpid,internalMap);
		}
		if(internalMap.containsKey(addr)) return false;
		internalMap.put(addr,port);
		return true;
	}
	public boolean removeLocation(DatapathId dpid,IPv4Address addr){
		HashMap<IPv4Address,OFPort> internalMap = locationMap.get(dpid);
		if(internalMap == null) return false;
		if(internalMap.containsKey(addr)){
			internalMap.remove(addr);
			return true;
		}
		return false;
	}
	public void removeIPFromAll(IPv4Address addr){
		for(DatapathId dpid : locationMap.keySet()){
			HashMap<IPv4Address,OFPort> internalMap = locationMap.get(dpid);
			if(internalMap.containsKey(addr)){
				internalMap.remove(addr);
			}
		}
	}
	public boolean containsSwitch(DatapathId id){
		return locationMap.containsKey(id);
	}
	public boolean containsIP(DatapathId dpid,IPv4Address addr){
		HashMap<IPv4Address,OFPort> internalMap = locationMap.get(dpid);
		if(internalMap == null) return false;
		return internalMap.containsKey(addr);
	}
	public void removeSwitch(DatapathId id){
		if(locationMap.containsKey(id)) 
			locationMap.remove(id);
	}
	public OFPort getLocation(DatapathId dpid,IPv4Address addr){
		HashMap<IPv4Address,OFPort> internalMap = locationMap.get(dpid);
		if(internalMap == null) return null;
		return internalMap.get(addr);
	}
}
