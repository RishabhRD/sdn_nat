package net.floodlightcontroller.sdn_nat;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Properties;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.MacAddress;

public class NetworkProperty{
	//IPv4 Related variables
	private IPv4Address gatewayIPv4Address;
	private IPv4Address globalIPv4;
	private IPv4AddressWithMask ipv4Subnet;
	//IPv6 related variables
	private IPv6Address globalIPv6;
	//MAC address related variables
	private MacAddress gatewayMac;
	private MacAddress globalGatewayMac;
	public NetworkProperty(IPv4Address gatewayIPv4Address,IPv4Address globalIPv4, IPv4AddressWithMask ipv4Subnet, IPv6Address globalIPv6, MacAddress gatewayMac, MacAddress globalGatewayMac){
		this.gatewayIPv4Address = gatewayIPv4Address;
		this.globalIPv4 = globalIPv4;
		this.ipv4Subnet = ipv4Subnet;
		this.globalIPv6 = globalIPv6;
		this.gatewayMac = gatewayMac;
		this.globalGatewayMac = globalGatewayMac;
	}
	public IPv4Address getGlobalIPv4() {
		return globalIPv4;
	}
	public IPv4Address getGatewayIPv4Address() {
		return gatewayIPv4Address;
	}
	public void setGatewayIPv4Address(IPv4Address gatewayIPv4Address) {
		this.gatewayIPv4Address = gatewayIPv4Address;
	}
	public void setGlobalIPv4(IPv4Address globalIPv4) {
		this.globalIPv4 = globalIPv4;
	}
	public IPv4AddressWithMask getIPv4Subnet() {
		return ipv4Subnet;
	}
	public void setIPv4Subnet(IPv4AddressWithMask subnet) {
		this.ipv4Subnet = subnet;
	}
	public IPv6Address getGlobalIPv6() {
		return globalIPv6;
	}
	public void setGlobalIPv6(IPv6Address globalIPv6) {
		this.globalIPv6 = globalIPv6;
	}
	public MacAddress getGatewayMac() {
		return gatewayMac;
	}
	public void setGatewayMac(MacAddress gatewayMac) {
		this.gatewayMac = gatewayMac;
	}
	public MacAddress getGlobalGatewayMac() {
		return globalGatewayMac;
	}
	public void setGlobalGatewayMac(MacAddress globalGatewayMac) {
		this.globalGatewayMac = globalGatewayMac;
	}
	public static NetworkProperty  from(String fileName) throws FileNotFoundException,IOException{
		FileInputStream fis = null;
		Properties prop = null;
		fis = new FileInputStream(fileName);
		prop = new Properties();
		prop.load(fis);
		fis.close();
		String gateway_ipv4 = prop.getProperty("gateway_ipv4");
		String global_ipv4 = prop.getProperty("global_ipv4");
		String ipv4_subnet = prop.getProperty("ipv4_subnet");
		String global_ipv6 = prop.getProperty("global_ipv6");
		String gateway_mac = prop.getProperty("gateway_mac");
		String global_gateway_mac = prop.getProperty("global_gateway_mac");
		if(gateway_ipv4 == null) 
			throw new InvalidParameterException("Property Not Found: gateway_ipv4");
		if(global_ipv4 == null) 
			throw new InvalidParameterException("Property Not Found: global_ipv4");
		if(ipv4_subnet== null) 
			throw new InvalidParameterException("Property Not Found: ipv4_subnet");
		if(global_ipv6 == null) 
			throw new InvalidParameterException("Property Not Found: global_ipv6");
		if(global_gateway_mac == null) 
			throw new InvalidParameterException("Property Not Found: global_gateway_mac");
		if(gateway_mac == null)
			throw new InvalidParameterException("Property Not Found: gateway_mac");
		NetworkProperty nwp = new NetworkProperty(
				IPv4Address.of(gateway_ipv4),
				IPv4Address.of(global_ipv4),
				IPv4AddressWithMask.of(ipv4_subnet),
				IPv6Address.of(global_ipv6),
				MacAddress.of(gateway_mac),
				MacAddress.of(global_gateway_mac));
		return nwp;

	}
}
