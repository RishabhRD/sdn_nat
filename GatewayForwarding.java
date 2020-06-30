package net.floodlightcontroller.sdn_nat;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;

import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.IPv6;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;

public class GatewayForwarding{
	private boolean routingEnabled = true;
	private IRoutingService routingService;
	private NodePortTuple gatewayAttachPoint;
	private HostMap hostMap;
	private NetworkProperty nwp;
	private TransportLayerPortPool portPool;
	private IOFSwitchService switchService;
	private HostLocationMap locationMap;
	private Logger log;

	private int DEFAULT_TABLE_ID = 0;
	private int DEFAULT_IDLE_TIMEOUT = 120;
	public static final int APP_ID = 5;
	public static final int APP_ID_BITS = 13;
	public static final int APP_ID_SHIFT = 64 - APP_ID_BITS;
	public static final long COOKIE = (long) (APP_ID & ((1 << APP_ID_BITS) - 1)) << APP_ID_SHIFT;

	public GatewayForwarding(NetworkProperty nwp,NodePortTuple gatewayAttachPoint,IRoutingService routingService,IOFSwitchService switchService,Logger log) {
		this.nwp = nwp;
		this.gatewayAttachPoint = gatewayAttachPoint;
		this.routingService = routingService;
		this.switchService = switchService;
		this.locationMap = new HostLocationMap();
		this.log = log;
		hostMap = new HostMap();
		portPool = new TransportLayerPortPool();
		for(int i = 1;i<=65535;i++){
			portPool.addPortToPool(i);
		}
	}
	public Command handlePacketIn(IOFSwitch sw,Ethernet eth, OFPort inPort){
		if(inPort.equals(OFPort.LOCAL)) return Command.CONTINUE;
		IPv4 ipv4 = null;
		IPv6 ipv6 = null;
		if(isIPv4(eth)){
			IPv4 ip = (IPv4) eth.getPayload();
			if(nwp.getIPv4Subnet().contains(ip.getSourceAddress()))
				locationMap.addLocation(sw.getId(),ip.getSourceAddress().toInetAddress(),inPort);
			if(nwp.getIPv4Subnet().contains(ipv4.getSourceAddress()) && nwp.getIPv4Subnet().contains(ipv4.getDestinationAddress()))
				return Command.CONTINUE;
			ipv4 = ip;
		}else{
			IPv6 ip = (IPv6) eth.getPayload();
			if(nwp.getIPv6Subnet().contains(ip.getSourceAddress()))
				locationMap.addLocation(sw.getId(),ip.getSourceAddress().toInetAddress(),inPort);
			if(nwp.getIPv6Subnet().contains(ipv6.getSourceAddress()) && nwp.getIPv6Subnet().contains(ipv6.getDestinationAddress()))
				return Command.CONTINUE;
			ipv6 = ip;
		}
		if(!routingEnabled) return Command.STOP;
		if(nwp.getIPv4Subnet().contains(ipv4.getSourceAddress()) && !nwp.getIPv4Subnet().contains(ipv4.getDestinationAddress())){
			if(sw.getId().equals(gatewayAttachPoint.getNodeId())){
				Integer srcTransportPort = installGatewayRules(sw,eth);
				if(srcTransportPort < 0){
					return Command.STOP;
				}
				pushPacketToGateway(sw,eth,srcTransportPort);
			}else{
				Path path = routingService.getPath(sw.getId(),gatewayAttachPoint.getNodeId());
				if(path.getPath().size() == 0){
					return Command.STOP;
				}
				boolean install = true;
				for(NodePortTuple tuple : path.getPath()){
					if(!install){
						locationMap.addLocation(tuple.getNodeId(),ipv4.getSourceAddress().toInetAddress(),tuple.getPortId());
						install = true;
						continue;
					}
					IOFSwitch currentSwitch = switchService.getSwitch(tuple.getNodeId());
					Match match = currentSwitch.getOFFactory().buildMatch().setExact(MatchField.ETH_TYPE,EthType.IPv4).setExact(MatchField.IPV4_DST,ipv4.getDestinationAddress()).build();
					ArrayList<OFAction> actions = new ArrayList<>();
					OFAction outputAction = currentSwitch.getOFFactory().actions().buildOutput().setPort(tuple.getPortId()).build();
					actions.add(outputAction);
					OFFlowAdd flowAdd  = currentSwitch.getOFFactory().buildFlowAdd().setCookie(U64.of(COOKIE)).setMatch(match).setActions(actions).setPriority(30).setIdleTimeout(DEFAULT_IDLE_TIMEOUT).setTableId(TableId.of(DEFAULT_TABLE_ID)).build();
					currentSwitch.write(flowAdd);
				}
				NodePortTuple tuple = path.getPath().get(0);
				IOFSwitch firstSwitch = switchService.getSwitch(tuple.getNodeId());
				ArrayList<OFAction> actions = new ArrayList<>();
				OFAction outputAction = firstSwitch.getOFFactory().actions().buildOutput().setPort(tuple.getPortId()).setMaxLen(Integer.MAX_VALUE).build();
				actions.add(outputAction);
				OFPacketOut packetOut = firstSwitch.getOFFactory().buildPacketOut().setData(eth.serialize()).setBufferId(OFBufferId.NO_BUFFER).setInPort(OFPort.CONTROLLER).setActions(actions).build();
				sw.write(packetOut);
				installGatewayRules(switchService.getSwitch(gatewayAttachPoint.getNodeId()),eth);
			}
		}else if ((!nwp.getIPv4Subnet().contains(ipv4.getSourceAddress())) && ipv4.getDestinationAddress().equals(nwp.getGlobalIPv4())){
			if(sw.getId().equals(gatewayAttachPoint.getNodeId())){
				if(!installReverseGatewayRules(sw,eth)){
					return Command.STOP;
				}
				TransportPort newPort = null;
				if(ipv4.getProtocol().equals(IpProtocol.UDP)){
					UDP udp = (UDP) ipv4.getPayload();
					newPort = udp.getDestinationPort();
				}else if(ipv4.getProtocol().equals(IpProtocol.TCP)){
					TCP tcp = (TCP) ipv4.getPayload();
					newPort = tcp.getDestinationPort();
				}else{
					return Command.CONTINUE;
				}
				Integer actualPort = hostMap.getMappedPort(newPort.getPort());
				if(actualPort == null) return Command.STOP;
				InetAddress inetAddr = hostMap.getMappedIP(newPort.getPort());
				if(inetAddr == null) return Command.STOP;
				IPv4Address destinationIP = IPv4Address.of((Inet4Address)inetAddr);
				MacAddress destinationMac = hostMap.getMappedMac(newPort.getPort());
				eth.setSourceMACAddress(nwp.getGatewayMac());
				eth.setDestinationMACAddress(destinationMac);
				ipv4.setDestinationAddress(destinationIP);
				if(ipv4.getProtocol().equals(IpProtocol.UDP)){
					UDP udp = (UDP) ipv4.getPayload();
					udp.setDestinationPort(TransportPort.of(actualPort));
				}else if(ipv4.getProtocol().equals(IpProtocol.TCP)){
					TCP tcp = (TCP) ipv4.getPayload();
					tcp.setDestinationPort(actualPort);
				}
				OFPort destinationPort = locationMap.getLocation(sw.getId(),destinationIP.toInetAddress());
				if(destinationPort == null) return Command.STOP;
				ArrayList<OFAction> actionList = new ArrayList<>();
				OFAction outputAction = sw.getOFFactory().actions().buildOutput().setPort(destinationPort).build();
				actionList.add(outputAction);
				OFPacketOut packetOut = sw.getOFFactory().buildPacketOut().setData(eth.serialize()).setActions(actionList).setInPort(OFPort.CONTROLLER).setBufferId(OFBufferId.NO_BUFFER).build();
				sw.write(packetOut);
			}else{
				OFPort destinationPort = locationMap.getLocation(sw.getId(),ipv4.getDestinationAddress().toInetAddress());
				if(destinationPort == null) return Command.STOP;
				ArrayList<OFAction> actionList = new ArrayList<>();
				OFAction action = sw.getOFFactory().actions().buildOutput().setPort(destinationPort).build();
				actionList.add(action);
				OFPacketOut packetOut = sw.getOFFactory().buildPacketOut().setData(eth.serialize()).setBufferId(OFBufferId.NO_BUFFER).setInPort(OFPort.CONTROLLER).setActions(actionList).build();
				sw.write(packetOut);
				Match match = sw.getOFFactory().buildMatch().setExact(MatchField.ETH_TYPE,EthType.IPv4)
						.setExact(MatchField.IPV4_DST,ipv4.getDestinationAddress()).build();
				OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd().setCookie(U64.of(COOKIE)).setTableId(TableId.of(1))
					.setMatch(match)
					.setPriority(30)
					.setIdleTimeout(DEFAULT_IDLE_TIMEOUT)
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
	private Integer installGatewayRules(IOFSwitch sw,Ethernet eth){
		Match match = createMatchFromPacket(sw,eth);
		if(match == null) return -1;
		TCP tcp = null;
		UDP udp = null;
		InetAddress srcIP = null;
		if(isIPv4(eth)){
			IPv4 ip = (IPv4) eth.getPayload();
			srcIP = ip.getSourceAddress().toInetAddress();
			if(ip.getProtocol().equals(IpProtocol.TCP)){
				tcp = (TCP) ip.getPayload();
			}else{
				udp = (UDP) ip.getPayload();
			}
		}else{
			IPv6 ip = (IPv6) eth.getPayload();
			srcIP = ip.getSourceAddress().toInetAddress();
			if(ip.getNextHeader().equals(IpProtocol.TCP)){
				tcp = (TCP) ip.getPayload();
			}else{
				udp = (UDP) ip.getPayload();
			}
		}
		TransportPort packetSrcTransportPort = null;
		if(tcp != null){
			packetSrcTransportPort = tcp.getDestinationPort();
		}else{
			packetSrcTransportPort = udp.getDestinationPort();
		}
		Integer srcTransportPort = portPool.getPortFromPool();
		if(srcTransportPort == null) return -1;
		hostMap.addMapping(srcTransportPort,eth.getSourceMACAddress(),srcIP,packetSrcTransportPort.getPort());
		OFOxms oxms = sw.getOFFactory().oxms();
		OFAction srcMacAction = sw.getOFFactory().actions().buildSetField().setField(oxms.ethSrc(nwp.getGatewayMac())).build();
		OFAction destMacAction = sw.getOFFactory().actions().buildSetField().setField(oxms.ethDst(nwp.getGlobalGatewayMac())).build();
		OFAction srcIPChangeAction = null;
		if(isIPv4(eth)){
			srcIPChangeAction = sw.getOFFactory().actions().buildSetField().setField(oxms.ipv6Src(nwp.getGlobalIPv6())).build();
		}else{
			srcIPChangeAction = sw.getOFFactory().actions().buildSetField().setField(oxms.ipv6Src(nwp.getGlobalIPv6())).build();
		}
		OFAction srcTransportPortChangeAction = null;
		if(tcp!=null){
			srcTransportPortChangeAction = sw.getOFFactory().actions().buildSetField().setField(oxms.tcpSrc(TransportPort.of(srcTransportPort))).build();
		}else{
			srcTransportPortChangeAction = sw.getOFFactory().actions().buildSetField().setField(oxms.udpSrc(TransportPort.of(srcTransportPort))).build();
		}
		OFAction outputAction = sw.getOFFactory().actions().buildOutput().setPort(gatewayAttachPoint.getPortId()).build();
		ArrayList<OFAction> actionList = new ArrayList<>();
		actionList.add(srcMacAction);
		actionList.add(destMacAction);
		actionList.add(srcIPChangeAction);
		actionList.add(srcTransportPortChangeAction);
		actionList.add(outputAction);
		OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd().setCookie(U64.of(COOKIE)).setMatch(match).setPriority(30).setIdleTimeout(DEFAULT_IDLE_TIMEOUT).setTableId(TableId.of(DEFAULT_TABLE_ID)).setActions(actionList).build();
		sw.write(flowAdd);
		return srcTransportPort;
	}
	private boolean installReverseGatewayRules(IOFSwitch sw,Ethernet eth ){
		Match match = createMatchFromPacket(sw,eth);
		if(match == null) return false;
		TCP tcp = null;
		UDP udp = null;
		if(isIPv4(eth)){
			IPv4 ip = (IPv4) eth.getPayload();
			if(ip.getProtocol().equals(IpProtocol.TCP)){
				tcp = (TCP) ip.getPayload();
			}else{
				udp = (UDP) ip.getPayload();
			}
		}else{
			IPv6 ip = (IPv6) eth.getPayload();
			if(ip.getNextHeader().equals(IpProtocol.TCP)){
				tcp = (TCP) ip.getPayload();
			}else{
				udp = (UDP) ip.getPayload();
			}
		}
		TransportPort packetDestTransportPort = null;
		if(tcp != null){
			packetDestTransportPort = tcp.getDestinationPort();
		}else{
			packetDestTransportPort = udp.getDestinationPort() ;
		}
		if(!hostMap.portExists(packetDestTransportPort.getPort())){
			return false;
		}
		MacAddress srcMac = nwp.getGatewayMac();
		MacAddress destMac = hostMap.getMappedMac(packetDestTransportPort.getPort());
		InetAddress destIP = hostMap.getMappedIP(packetDestTransportPort.getPort());
		Integer destTransportPort = hostMap.getMappedPort(packetDestTransportPort.getPort());
		OFPort destPort = locationMap.getLocation(sw.getId(),destIP);
		OFOxms oxms = sw.getOFFactory().oxms();
		OFAction srcMacChangeAction = sw.getOFFactory().actions().buildSetField().setField(oxms.ethSrc(srcMac)).build();
		OFAction destMacChangeAction = sw.getOFFactory().actions().buildSetField().setField(oxms.ethDst(destMac)).build();
		OFAction destIPChangeAction = null;
		if(isIPv4(eth)){
			destIPChangeAction = sw.getOFFactory().actions().buildSetField().setField(oxms.ipv4Dst(IPv4Address.of((Inet4Address)destIP))).build();
		}else{
			destIPChangeAction = sw.getOFFactory().actions().buildSetField().setField(oxms.ipv6Dst(IPv6Address.of((Inet6Address)destIP))).build();
		}
		OFAction destTransportPortChangeAction = null;
		if(tcp!=null){
			destTransportPortChangeAction = sw.getOFFactory().actions().buildSetField().setField(oxms.tcpDst(TransportPort.of(destTransportPort))).build();
		}else{
			destTransportPortChangeAction = sw.getOFFactory().actions().buildSetField().setField(oxms.udpDst(TransportPort.of(destTransportPort))).build();
		}
		OFAction outputAction = sw.getOFFactory().actions().buildOutput().setPort(destPort).build();
		ArrayList<OFAction> actionList = new ArrayList<>();
		actionList.add(srcMacChangeAction);
		actionList.add(destMacChangeAction);
		actionList.add(destIPChangeAction);
		actionList.add(destTransportPortChangeAction);
		actionList.add(outputAction);
		OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd().setCookie(U64.of(COOKIE))
			.setTableId(TableId.of(DEFAULT_TABLE_ID))
			.setIdleTimeout(DEFAULT_IDLE_TIMEOUT)
			.setActions(actionList)
			.setMatch(match)
			.build();
		sw.write(flowAdd);
		return true;
	}
	private Match createMatchFromPacket(IOFSwitch sw,Ethernet eth){
		Match.Builder match = sw.getOFFactory().buildMatch();
		match.setExact(MatchField.ETH_TYPE,eth.getEtherType());
		match.setExact(MatchField.ETH_SRC,eth.getSourceMACAddress());
		match.setExact(MatchField.ETH_DST,eth.getDestinationMACAddress());
		TCP tcp = null;
		UDP udp = null;
		if(isIPv4(eth)){
			IPv4 ip = (IPv4) eth.getPayload();
			match.setExact(MatchField.IPV4_DST,ip.getDestinationAddress());
			match.setExact(MatchField.IPV4_SRC,ip.getSourceAddress());
			if(ip.getProtocol().equals(IpProtocol.TCP)) {
				tcp = (TCP) ip.getPayload();
				match.setExact(MatchField.IP_PROTO,IpProtocol.TCP);
			}
			else if(ip.getProtocol().equals(IpProtocol.UDP)){
				udp = (UDP) ip.getPayload();
				match.setExact(MatchField.IP_PROTO,IpProtocol.UDP);
			}
			else return null;
		}else if(isIPv6(eth)){
			IPv6 ip = (IPv6) eth.getPayload();
			match.setExact(MatchField.IPV6_DST,ip.getDestinationAddress());
			match.setExact(MatchField.IPV6_SRC,ip.getSourceAddress());
			if(ip.getNextHeader().equals(IpProtocol.TCP)) {
				tcp = (TCP) ip.getPayload();
				match.setExact(MatchField.IP_PROTO,IpProtocol.TCP);
			}
			else if(ip.getNextHeader().equals(IpProtocol.UDP)){
				udp = (UDP) ip.getPayload();
				match.setExact(MatchField.IP_PROTO,IpProtocol.UDP);
			}
			else return null;
		}else{
			return null;
		}
		if(tcp != null){
			match.setExact(MatchField.TCP_SRC,tcp.getSourcePort());
			match.setExact(MatchField.TCP_DST,tcp.getDestinationPort());
		}else{
			match.setExact(MatchField.UDP_SRC,udp.getSourcePort());
			match.setExact(MatchField.UDP_DST,udp.getDestinationPort());
		}
		return match.build();
	}
	private boolean isIPv4(Ethernet eth){
		if(eth.getEtherType().equals(EthType.IPv4)) return true;
		return false;
	}
	private boolean isIPv6(Ethernet eth){
		if(eth.getEtherType().equals(EthType.IPv6)) return true;
		return false;
	}
	private void pushPacketToGateway(IOFSwitch sw, Ethernet eth,Integer srcTransportPort){
		eth.setSourceMACAddress(nwp.getGatewayMac());
		eth.setDestinationMACAddress(nwp.getGlobalGatewayMac());
		TCP tcp = null;
		UDP udp = null;
		if(isIPv4(eth)){
			IPv4 ip = (IPv4) eth.getPayload();
			ip.setSourceAddress(nwp.getGlobalIPv4());
			if(ip.getProtocol().equals(IpProtocol.TCP)){
				tcp = (TCP) ip.getPayload();
			}else{
				udp = (UDP) ip.getPayload();
			}
		}else{
			IPv6 ip = (IPv6) eth.getPayload();
			ip.setSourceAddress(nwp.getGlobalIPv6());
			if(ip.getNextHeader().equals(IpProtocol.TCP)){
				tcp = (TCP) ip.getPayload();
			}else{
				udp = (UDP) ip.getPayload();
			}
		}
		if(tcp!=null){
			tcp.setSourcePort(TransportPort.of(srcTransportPort));
		}else{
			udp.setSourcePort(TransportPort.of(srcTransportPort));
		}
		ArrayList<OFAction> actionList = new ArrayList<>();
		OFAction outputAction = sw.getOFFactory().actions().buildOutput().setPort(gatewayAttachPoint.getPortId()).build();
		actionList.add(outputAction);
		OFPacketOut packetOut = sw.getOFFactory().buildPacketOut().setData(eth.serialize()).setBufferId(OFBufferId.NO_BUFFER).setInPort(OFPort.CONTROLLER).setActions(actionList).build();
		sw.write(packetOut);
	}
	private void pushPacketToReverseGateway(IOFSwitch sw,Ethernet eth){
	}
}
