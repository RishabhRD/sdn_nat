package net.floodlightcontroller.sdn_nat;

import java.util.Collection;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.routing.IRoutingService;

public class NAT implements IFloodlightModule, IOFMessageListener{

	protected IFloodlightProviderService floodlightProviderService;
	private NodePortTuple gatewayAttachPoint;
	private IPv4Address   globalIP;
	private IPv4AddressWithMask subnet;
	private ARPProxy proxy;
	private GatewayForwarding forwarding;
	private IPv4Address gatewayIP;
	private MacAddress gatewayMac ;
	private MacAddress globalGatewayMac;
	private IRoutingService routingService;
	private IOFSwitchService switchService;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		return null;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		gatewayAttachPoint = new NodePortTuple(DatapathId.of("0000b2823855c246"),OFPort.of(1));
		gatewayIP = IPv4Address.of("10.0.0.1");
		subnet = IPv4AddressWithMask.of("10.0.0.0/24");
		globalIP = IPv4Address.of("192.168.43.106");
		globalGatewayMac = MacAddress.of("c8:3d:dc:e6:97:7a");
		gatewayMac = MacAddress.of("3c:95:09:20:a1:67");
		routingService = context.getServiceImpl(IRoutingService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		proxy = new ARPProxy(gatewayMac,gatewayIP);
		forwarding = new GatewayForwarding(gatewayMac,globalGatewayMac,globalIP,subnet,gatewayAttachPoint,routingService,switchService);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN,this);
	}

	@Override
	public String getName() {
		return "nat";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return name.equals("forwarding");
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		OFPacketIn pi = (OFPacketIn) msg;
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT));
		if(eth.getEtherType().equals(EthType.ARP)){
			ARP arp = (ARP) eth.getPayload();
			proxy.handlePacketIn(sw,arp,inPort);
		}else if(eth.getEtherType().equals(EthType.IPv4)){
			forwarding.handlePacketIn(sw,eth,inPort);
		}
		return Command.CONTINUE;
	}
}
