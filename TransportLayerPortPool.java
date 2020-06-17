package net.floodlightcontroller.sdn_nat;

import java.util.NoSuchElementException;
import java.util.TreeSet;

public class TransportLayerPortPool{
	private TreeSet<Integer> set;
	public TransportLayerPortPool(){
		set = new TreeSet<>();
	}
	public void addPortToPool(Integer port){
		set.add(port);
	}
	public Integer getPortFromPool() throws NoSuchElementException{
		return set.first();
	}
	public boolean isEmpty(){
		return set.isEmpty();
	}
	public boolean isInPool(Integer port){
		if(set.isEmpty()) return false;
		return set.contains(port);
	}
}
