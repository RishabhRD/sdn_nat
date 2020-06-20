package net.floodlightcontroller.sdn_nat;

import java.util.ArrayList;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;

public class GatewayForwarding{
	private boolean routingEnabled = true;
	private IRoutingService routingService;
	private IPv4Address globalIp;
	private NodePortTuple gatewayAttachPoint;
	private HostMap hostMap;
	private IPv4AddressWithMask subnet;
//	private MacAddress gatewayMac;
	private TransportLayerPortPool portPool;
	private IOFSwitchService switchService;
	private HostLocationMap locationMap;

	public static final int APP_ID = 5;
	public static final int APP_ID_BITS = 13;
	public static final int APP_ID_SHIFT = 64 - APP_ID_BITS;
	public static final long COOKIE = (long) (APP_ID & ((1 << APP_ID_BITS) - 1)) << APP_ID_SHIFT;

	public GatewayForwarding(IPv4Address globalIp,/*MacAddress gatewayMac,*/IPv4AddressWithMask subnet,NodePortTuple gatewayAttachPoint,IRoutingService routingService,IOFSwitchService switchService) {
		this.globalIp = globalIp;
//		this.gatewayMac = gatewayMac;
		this.subnet = subnet;
		this.gatewayAttachPoint = gatewayAttachPoint;
		this.routingService = routingService;
		this.switchService = switchService;
		this.locationMap = new HostLocationMap();
		hostMap = new HostMap();
		portPool = new TransportLayerPortPool();
		for(int i = 1;i<65535;i++){
			portPool.addPortToPool(i);
		}
	}
	public Command handlePacketIn(IOFSwitch sw,Ethernet ethernet, OFPort inPort){
		IPv4 ip = (IPv4) ethernet.getPayload();
		if(subnet.contains(ip.getSourceAddress())){
			locationMap.addLocation(sw.getId(),ip.getSourceAddress(),inPort);
		}
		if(subnet.contains(ip.getSourceAddress()) && subnet.contains(ip.getDestinationAddress())){
			return Command.CONTINUE;
		}
		if(!routingEnabled) return Command.STOP;
		if(!subnet.contains(ip.getDestinationAddress())){
			if(sw.getId().equals(gatewayAttachPoint.getNodeId())){
				IOFSwitch firstSwitch = switchService.getSwitch(gatewayAttachPoint.getNodeId());
				Integer newPort = portPool.getPortFromPool();
				if(newPort == null) return Command.STOP;
				ArrayList<OFAction> actionList = new ArrayList<>();
				OFAction portChangeAction = sw.getOFFactory().actions().buildSetTpSrc().setTpPort(TransportPort.of(newPort)).build();
				OFAction ipChangeAction = sw.getOFFactory().actions().buildSetNwSrc().setNwAddr(globalIp).build();
				OFAction outputAction = sw.getOFFactory().actions().buildOutput().setPort(gatewayAttachPoint.getPortId()).build();
				actionList.add(ipChangeAction);
				actionList.add(portChangeAction);
				actionList.add(outputAction);
				OFPacketOut packetOut = firstSwitch.getOFFactory().buildPacketOut().setData(ethernet.serialize()).setInPort(OFPort.ANY).setBufferId(OFBufferId.NO_BUFFER).setActions(actionList).build();
				firstSwitch.write(packetOut);
				installGatewayRules(sw.getId(),ethernet,newPort);
			}else{
				Path path = routingService.getPath(sw.getId(),gatewayAttachPoint.getNodeId());
				if(path.getPath().size() == 0){
					return Command.STOP;
				}
				boolean install = true;
				for(NodePortTuple tuple : path.getPath()){
					if(!install){
						install = true;
						continue;
					}
					IOFSwitch currentSwitch = switchService.getSwitch(tuple.getNodeId());
					Match match = currentSwitch.getOFFactory().buildMatch().setExact(MatchField.ETH_TYPE,EthType.IPv4).setExact(MatchField.IPV4_DST,ip.getDestinationAddress()).build();
					ArrayList<OFAction> actions = new ArrayList<>();
					OFAction outputAction = currentSwitch.getOFFactory().actions().buildOutput().setPort(tuple.getPortId()).build();
					actions.add(outputAction);
					OFFlowAdd flowAdd  = currentSwitch.getOFFactory().buildFlowAdd().setCookie(U64.of(COOKIE)).setMatch(match).setActions(actions).setPriority(30).setIdleTimeout(10).setHardTimeout(10).setTableId(TableId.of(0)).build();
					currentSwitch.write(flowAdd);
				}
				NodePortTuple tuple = path.getPath().get(0);
				IOFSwitch firstSwitch = switchService.getSwitch(tuple.getNodeId());
				ArrayList<OFAction> actions = new ArrayList<>();
				OFAction outputAction = firstSwitch.getOFFactory().actions().buildOutput().setPort(tuple.getPortId()).setMaxLen(Integer.MAX_VALUE).build();
				actions.add(outputAction);
				OFPacketOut packetOut = firstSwitch.getOFFactory().buildPacketOut().setData(ethernet.serialize()).setInPort(OFPort.ANY).setBufferId(OFBufferId.NO_BUFFER).setActions(actions).build();
				sw.write(packetOut);
				installGatewayRules(gatewayAttachPoint.getNodeId(),ethernet);
			}
		}else{
			if(sw.getId().equals(gatewayAttachPoint.getNodeId())){
				if(!installReverseGatewayRules(ethernet,sw)){
					return Command.STOP;
				}
				TransportPort newPort = null;
				if(ip.getProtocol().equals(IpProtocol.UDP)){
					UDP udp = (UDP) ip.getPayload();
					newPort = udp.getDestinationPort();
				}else if(ip.getProtocol().equals(IpProtocol.TCP)){
					TCP tcp = (TCP) ip.getPayload();
					newPort = tcp.getDestinationPort();
				}else{
					return Command.CONTINUE;
				}
				Integer actualPort = hostMap.getMappedPort(newPort.getPort());
				if(actualPort == null) return Command.STOP;
				IPv4Address destinationIP = hostMap.getMappedIP(newPort.getPort());
				MacAddress destinationMac = hostMap.getMappedMac(newPort.getPort());
				ethernet.setDestinationMACAddress(destinationMac);
				ip.setDestinationAddress(destinationIP);
				if(ip.getProtocol().equals(IpProtocol.UDP)){
					UDP udp = (UDP) ip.getPayload();
					udp.setDestinationPort(TransportPort.of(actualPort));
				}else if(ip.getProtocol().equals(IpProtocol.TCP)){
					TCP tcp = (TCP) ip.getPayload();
					tcp.setDestinationPort(actualPort);
				}
				OFPort destinationPort = locationMap.getLocation(sw.getId(),destinationIP);
				if(destinationPort == null) return Command.STOP;
				ArrayList<OFAction> actionList = new ArrayList<>();
				OFAction outputAction = sw.getOFFactory().actions().buildOutput().setPort(destinationPort).build();
				actionList.add(outputAction);
				OFPacketOut packetOut = sw.getOFFactory().buildPacketOut().setData(ethernet.serialize()).setInPort(OFPort.ANY).setActions(actionList).setBufferId(OFBufferId.NO_BUFFER).build();
				sw.write(packetOut);
			}else{
				OFPort destinationPort = locationMap.getLocation(sw.getId(),ip.getDestinationAddress());
				if(destinationPort == null) return Command.STOP;
				ArrayList<OFAction> actionList = new ArrayList<>();
				OFAction action = sw.getOFFactory().actions().buildOutput().setPort(destinationPort).build();
				actionList.add(action);
				OFPacketOut packetOut = sw.getOFFactory().buildPacketOut().setData(ethernet.serialize()).setInPort(OFPort.ANY).setBufferId(OFBufferId.NO_BUFFER).setActions(actionList).build();
				sw.write(packetOut);
				Match match = sw.getOFFactory().buildMatch().setExact(MatchField.ETH_TYPE,EthType.IPv4)
						.setExact(MatchField.IPV4_DST,ip.getDestinationAddress()).build();
				OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd().setCookie(U64.of(COOKIE)).setTableId(TableId.of(1))
					.setMatch(match)
					.setPriority(30)
					.setIdleTimeout(10)
					.setActions(actionList)
					.build();
				sw.write(flowAdd);
			}
		}
		return Command.STOP;
		
	}
	public void setRoutingEnabled(boolean enabled){
		this.routingEnabled = enabled;
	}
	private boolean installGatewayRules(DatapathId id,Ethernet ethernet){
		IOFSwitch sw = switchService.getSwitch(id);
		IPv4 ip = (IPv4) ethernet.getPayload();
		TransportPort port = null;
		Match match = null;
		if(ip.getProtocol().equals(IpProtocol.UDP)){
			UDP udp = (UDP) ip.getPayload();
			port = udp.getSourcePort();
			match = sw.getOFFactory().buildMatch().setExact(MatchField.ETH_TYPE,EthType.IPv4).
				setExact(MatchField.IPV4_SRC,ip.getSourceAddress()).
				setExact(MatchField.IPV4_DST,ip.getDestinationAddress()).
				setExact(MatchField.IP_PROTO,IpProtocol.UDP).
				setExact(MatchField.UDP_SRC,udp.getSourcePort()).
				setExact(MatchField.UDP_DST,udp.getDestinationPort()).build();
		}else if(ip.getProtocol().equals(IpProtocol.TCP)){
			TCP tcp = (TCP) ip.getPayload();
			port = tcp.getSourcePort();
			match = sw.getOFFactory().buildMatch().setExact(MatchField.ETH_TYPE,EthType.IPv4).
				setExact(MatchField.IPV4_SRC,ip.getSourceAddress()).
				setExact(MatchField.IPV4_DST,ip.getDestinationAddress()).
				setExact(MatchField.IP_PROTO,IpProtocol.UDP).
				setExact(MatchField.TCP_SRC,tcp.getSourcePort()).
				setExact(MatchField.TCP_DST,tcp.getDestinationPort()).build();
		}else{
			return false;
		}
		Integer newPort = portPool.getPortFromPool();
		if(newPort == null) return false;
		hostMap.addMapping(newPort,ethernet.getSourceMACAddress(),ip.getSourceAddress(),port.getPort());
		ArrayList<OFAction> actionList = new ArrayList<>();
		OFAction portChangeAction = sw.getOFFactory().actions().buildSetTpSrc().setTpPort(TransportPort.of(newPort)).build();
		OFAction ipChangeAction = sw.getOFFactory().actions().buildSetNwSrc().setNwAddr(globalIp).build();
		OFAction outputAction = sw.getOFFactory().actions().buildOutput().setPort(gatewayAttachPoint.getPortId()).build();
		actionList.add(ipChangeAction);
		actionList.add(portChangeAction);
		actionList.add(outputAction);
		OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd().setCookie(U64.of(COOKIE)).setMatch(match).setPriority(30).setIdleTimeout(5).setTableId(TableId.of(0)).setActions(actionList).build();
		sw.write(flowAdd);
		return true;
	}
	private boolean installGatewayRules(DatapathId id,Ethernet ethernet,Integer newPort){
		IOFSwitch sw = switchService.getSwitch(id);
		IPv4 ip = (IPv4) ethernet.getPayload();
		TransportPort port = null;
		Match match = null;
		if(ip.getProtocol().equals(IpProtocol.UDP)){
			UDP udp = (UDP) ip.getPayload();
			port = udp.getSourcePort();
			match = sw.getOFFactory().buildMatch().setExact(MatchField.ETH_TYPE,EthType.IPv4).
				setExact(MatchField.IPV4_SRC,ip.getSourceAddress()).
				setExact(MatchField.IPV4_DST,ip.getDestinationAddress()).
				setExact(MatchField.IP_PROTO,IpProtocol.UDP).
				setExact(MatchField.UDP_SRC,udp.getSourcePort()).
				setExact(MatchField.UDP_DST,udp.getDestinationPort()).build();
		}else if(ip.getProtocol().equals(IpProtocol.TCP)){
			TCP tcp = (TCP) ip.getPayload();
			port = tcp.getSourcePort();
			match = sw.getOFFactory().buildMatch().setExact(MatchField.ETH_TYPE,EthType.IPv4).
				setExact(MatchField.IPV4_SRC,ip.getSourceAddress()).
				setExact(MatchField.IPV4_DST,ip.getDestinationAddress()).
				setExact(MatchField.IP_PROTO,IpProtocol.UDP).
				setExact(MatchField.TCP_SRC,tcp.getSourcePort()).
				setExact(MatchField.TCP_DST,tcp.getDestinationPort()).build();
		}else{
			return false;
		}
		if(newPort == null) return false;
		hostMap.addMapping(newPort,ethernet.getSourceMACAddress(),ip.getSourceAddress(),port.getPort());
		ArrayList<OFAction> actionList = new ArrayList<>();
		OFAction portChangeAction = sw.getOFFactory().actions().buildSetTpSrc().setTpPort(TransportPort.of(newPort)).build();
		OFAction ipChangeAction = sw.getOFFactory().actions().buildSetNwSrc().setNwAddr(globalIp).build();
		OFAction outputAction = sw.getOFFactory().actions().buildOutput().setPort(gatewayAttachPoint.getPortId()).build();
		actionList.add(ipChangeAction);
		actionList.add(portChangeAction);
		actionList.add(outputAction);
		OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd().setCookie(U64.of(COOKIE)).setMatch(match).setPriority(30).setIdleTimeout(5).setTableId(TableId.of(0)).setActions(actionList).build();
		sw.write(flowAdd);
		return true;
	}
	private boolean installReverseGatewayRules(Ethernet eth, IOFSwitch sw){
		IPv4 ip = (IPv4) eth.getPayload();
		OFPort destinationPort = locationMap.getLocation(sw.getId(),ip.getDestinationAddress());
		if(destinationPort == null) return false;
		Match match = null;
		TransportPort srcPort = null;
		if(ip.getProtocol().equals(IpProtocol.UDP)){
			UDP udp = (UDP) ip.getPayload();
			srcPort = udp.getSourcePort();
			match = sw.getOFFactory().buildMatch().setExact(MatchField.ETH_TYPE,EthType.IPv4)
				.setExact(MatchField.ETH_DST,eth.getDestinationMACAddress())
				.setExact(MatchField.IPV4_SRC,ip.getSourceAddress())
				.setExact(MatchField.IPV4_DST,ip.getDestinationAddress())
				.setExact(MatchField.IP_PROTO,IpProtocol.UDP)
				.setExact(MatchField.UDP_SRC,udp.getSourcePort())
				.setExact(MatchField.UDP_DST,udp.getDestinationPort())
				.build();
		}else if(ip.getProtocol().equals(IpProtocol.TCP)){
			TCP tcp = (TCP) ip.getPayload();
			srcPort = tcp.getSourcePort();
			match = sw.getOFFactory().buildMatch().setExact(MatchField.ETH_TYPE,EthType.IPv4)
				.setExact(MatchField.ETH_DST,eth.getDestinationMACAddress())
				.setExact(MatchField.IPV4_SRC,ip.getSourceAddress())
				.setExact(MatchField.IPV4_DST,ip.getDestinationAddress())
				.setExact(MatchField.IP_PROTO,IpProtocol.TCP)
				.setExact(MatchField.TCP_SRC,tcp.getSourcePort())
				.setExact(MatchField.TCP_DST,tcp.getDestinationPort())
				.build();
		}else{
			return false;
		}
		MacAddress destinationMac = hostMap.getMappedMac(srcPort.getPort());
		IPv4Address destinationIP = hostMap.getMappedIP(srcPort.getPort());
		Integer destinationTransportPort = hostMap.getMappedPort(srcPort.getPort());
		OFAction macChangeAction = sw.getOFFactory().actions().buildSetDlDst().setDlAddr(destinationMac).build();
		OFAction ipChangeAction = sw.getOFFactory().actions().buildSetNwDst().setNwAddr(destinationIP).build();
		OFAction portChangeAction = sw.getOFFactory().actions().buildSetTpDst().setTpPort(TransportPort.of(destinationTransportPort)).build();
		OFAction outputAction = sw.getOFFactory().actions().buildOutput().setPort(destinationPort).build();
		ArrayList<OFAction> actionList = new ArrayList<>();
		actionList.add(macChangeAction);
		actionList.add(ipChangeAction);
		actionList.add(portChangeAction);
		actionList.add(outputAction);
		OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd().setCookie(U64.of(COOKIE))
			.setTableId(TableId.of(0))
			.setIdleTimeout(10)
			.setActions(actionList)
			.setMatch(match)
			.build();
		sw.write(flowAdd);
		return true;
	}
}
