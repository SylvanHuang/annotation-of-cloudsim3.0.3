/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.provisioners;

import java.util.HashMap;
import java.util.Map;

import org.cloudbus.cloudsim.Vm;

/**
 * RamProvisionerSimple is an extension of RamProvisioner which uses a best-effort policy to
 * allocate memory to a VM.
 * 
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class RamProvisionerSimple extends RamProvisioner {

	/** The RAM table. */
	private Map<String, Integer> ramTable;

	/**
	 * Instantiates a new ram provisioner simple.
	 * 
	 * @param availableRam the available ram
	 */
	public RamProvisionerSimple(int availableRam) {
		super(availableRam);
		setRamTable(new HashMap<String, Integer>());
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.RamProvisioner#allocateRamForVm(cloudsim.Vm, int)
	 */
	@Override
	public boolean allocateRamForVm(Vm vm, int ram) {
		int maxRam = vm.getRam();//虚拟机所拥有的总的内存，所以也就是最大内存

		if (ram >= maxRam) {	//如果当前请求内存大于虚拟机的内存容量，
			ram = maxRam;
		}
		//释放此虚拟机在此主机上面已经分配的内存，这里不知道为什么？？？应该是不需要的
		deallocateRamForVm(vm);
		
		if (getAvailableRam() >= ram) {	//存在足够的可用内存
			setAvailableRam(getAvailableRam() - ram);
			getRamTable().put(vm.getUid(), ram);
			//???内存刚分配好，插入到了ramTable中，现在又调用getAllocatedRamForVm是否没有必要呢？
			vm.setCurrentAllocatedRam(getAllocatedRamForVm(vm));
			return true;
		}
		//
		vm.setCurrentAllocatedRam(getAllocatedRamForVm(vm));

		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.RamProvisioner#getAllocatedRamForVm(cloudsim.Vm)
	 */
	@Override
	public int getAllocatedRamForVm(Vm vm) {
		if (getRamTable().containsKey(vm.getUid())) {
			return getRamTable().get(vm.getUid());
		}
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.RamProvisioner#deallocateRamForVm(cloudsim.Vm)
	 */
	@Override
	public void deallocateRamForVm(Vm vm) {
		//首先判断此虚拟机是否已经分配了内存
		if (getRamTable().containsKey(vm.getUid())) {
			int amountFreed = getRamTable().remove(vm.getUid());
			setAvailableRam(getAvailableRam() + amountFreed);
			vm.setCurrentAllocatedRam(0);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.RamProvisioner#deallocateRamForVm(cloudsim.Vm)
	 */
	@Override
	public void deallocateRamForAllVms() {
		super.deallocateRamForAllVms();
		getRamTable().clear();
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.RamProvisioner#isSuitableForVm(cloudsim.Vm, int)
	 */
	@Override
	public boolean isSuitableForVm(Vm vm, int ram) {
		int allocatedRam = getAllocatedRamForVm(vm);
		boolean result = allocateRamForVm(vm, ram);
		deallocateRamForVm(vm);
		if (allocatedRam > 0) {
			allocateRamForVm(vm, allocatedRam);
		}
		return result;
	}

	/**
	 * Gets the ram table.
	 * 
	 * @return the ram table
	 */
	protected Map<String, Integer> getRamTable() {
		return ramTable;
	}

	/**
	 * Sets the ram table.
	 * 
	 * @param ramTable the ram table
	 */
	protected void setRamTable(Map<String, Integer> ramTable) {
		this.ramTable = ramTable;
	}

}
