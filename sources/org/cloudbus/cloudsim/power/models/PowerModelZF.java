package org.cloudbus.cloudsim.power.models;

public class PowerModelZF extends PowerModel {

	@Override
	public double getPower(double utilization) throws IllegalArgumentException {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public double getPower(double utilizationCpu,double utilizationMem) throws IllegalArgumentException {
		//check the hosts on/off according to the cpu utilization 
		if (utilizationCpu < 0 || utilizationCpu > 1 ) {
			throw new IllegalArgumentException("Utilization value must be between 0 and 1");
		}
		if (utilizationCpu == 0) {
			return 0;
		}
		double temp =155.057327270508
		+357.8550*utilizationCpu-401.0088*Math.pow(utilizationCpu, 2)+164.4327*Math.pow(utilizationCpu, 3)
		-30.6192*utilizationMem+41.8946*Math.pow(utilizationMem,2)-19.8122*Math.pow(utilizationMem, 3);
		return temp*1.2;
	}

}
