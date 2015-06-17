/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.provisioners;

import java.util.List;

import org.cloudbus.cloudsim.Vm;

/**
 * PE的供给策略 The Class PeProvisioner.
 * 	其中一个PePrivisioner同时包含了，它所拥有的，按照这种策略支配的处理能力。一个host不直接包含PE对象，而是包含了一个PeProvisioner的列表
 * 这样对于同一台主机，可以实现不同部分的处理能力，拥有不同的处理能力的分配策略。
 * 
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 2.0
 */
public abstract class PeProvisioner {

	/** 总的处理能力 （单位Million Instructions Per Second 每秒百万条指令）The mips. */
	private double mips;

	/** 剩余的处理能力The available mips. */
	private double availableMips;

	/**
	 * Creates the new PeProvisioner.
	 * 
	 * @param mips overall amount of MIPS available in the Pe
	 * 
	 * @pre mips>=0
	 * @post $none
	 */
	public PeProvisioner(double mips) {
		setMips(mips);
		setAvailableMips(mips);
	}

	/**
	 * 把处理能力分配给VM
	 * Allocates MIPS for a given VM.
	 * 
	 * @param vm virtual machine for which the MIPS are being allocated
	 * @param mips the mips
	 * 
	 * @return $true if the MIPS could be allocated; $false otherwise
	 * 
	 * @pre $none
	 * @post $none
	 */
	public abstract boolean allocateMipsForVm(Vm vm, double mips);

	/**
	 * Allocates MIPS for a given VM.
	 * 
	 * @param vmUid the vm uid
	 * @param mips the mips
	 * 
	 * @return $true if the MIPS could be allocated; $false otherwise
	 * 
	 * @pre $none
	 * @post $none
	 */
	public abstract boolean allocateMipsForVm(String vmUid, double mips);

	/**
	 * Allocates MIPS for a given VM.
	 * 
	 * @param vm virtual machine for which the MIPS are being allocated
	 * @param mips the mips for each virtual Pe
	 * 
	 * @return $true if the MIPS could be allocated; $false otherwise
	 * 
	 * @pre $none
	 * @post $none
	 */
	public abstract boolean allocateMipsForVm(Vm vm, List<Double> mips);

	/**
	 * Gets allocated MIPS for a given VM.
	 * 
	 * @param vm virtual machine for which the MIPS are being allocated
	 * 
	 * @return array of allocated MIPS
	 * 
	 * @pre $none
	 * @post $none
	 */
	public abstract List<Double> getAllocatedMipsForVm(Vm vm);

	/**
	 * Gets total allocated MIPS for a given VM for all PEs.
	 * 
	 * @param vm virtual machine for which the MIPS are being allocated
	 * 
	 * @return total allocated MIPS
	 * 
	 * @pre $none
	 * @post $none
	 */
	public abstract double getTotalAllocatedMipsForVm(Vm vm);

	/**
	 * Gets allocated MIPS for a given VM for a given virtual Pe.
	 * 
	 * @param vm virtual machine for which the MIPS are being allocated
	 * @param peId the pe id
	 * 
	 * @return allocated MIPS
	 * 
	 * @pre $none
	 * @post $none
	 */
	public abstract double getAllocatedMipsForVmByVirtualPeId(Vm vm, int peId);

	/**
	 * Releases MIPS used by a VM.
	 * 
	 * @param vm the vm
	 * 
	 * @pre $none
	 * @post none
	 */
	public abstract void deallocateMipsForVm(Vm vm);

	/**
	 * Releases MIPS used by all VMs.
	 * 
	 * @pre $none
	 * @post none
	 */
	public void deallocateMipsForAllVms() {
		setAvailableMips(getMips());
	}

	/**
	 * Gets the MIPS.
	 * 
	 * @return the MIPS
	 */
	public double getMips() {
		return mips;
	}

	/**
	 * Sets the MIPS.
	 * 
	 * @param mips the MIPS to set
	 */
	public void setMips(double mips) {
		this.mips = mips;
	}

	/**
	 * Gets the available MIPS in the PE.
	 * 
	 * @return available MIPS
	 * 
	 * @pre $none
	 * @post $none
	 */
	public double getAvailableMips() {
		return availableMips;
	}

	/**
	 * Sets the available MIPS.
	 * 
	 * @param availableMips the availableMips to set
	 */
	protected void setAvailableMips(double availableMips) {
		this.availableMips = availableMips;
	}

	/**
	 * Gets the total allocated MIPS.
	 * 
	 * @return the total allocated MIPS
	 */
	public double getTotalAllocatedMips() {
		double totalAllocatedMips = getMips() - getAvailableMips();
		if (totalAllocatedMips > 0) {
			return totalAllocatedMips;
		}
		return 0;
	}

	/**
	 * Gets the utilization of the Pe in percents.
	 * 
	 * @return the utilization
	 */
	public double getUtilization() {
		return getTotalAllocatedMips() / getMips();
	}

}
