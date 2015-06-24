/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.provisioners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Vm;

/**
 * The Class PeProvisionerSimple.
 * 将处理器能力量化，每为一台虚拟机成功分配后，处理器能力就相应的减少，
 * 如果当前可用处理器能力低于要分配的值，则分配失败，同时以列表的形式
 * 记录了同一台虚拟机多次分配的结果。此外，处理器能力的分配策略是在初
 * 始化Pe对象时指定的，构造方法为public Pe(int id, PeProvisioner peProvisioner)，
 * 这就意味着，一台主机中不同的处理器可以采用不同的分配策略。
 * 其中一个PePrivisioner同时包含了，它所拥有的，按照这种策略支配的处理能力。
 * 一个host不直接包含PE对象，而是包含了一个PeProvisioner的列表
 * 这样对于同一台主机，可以实现不同部分的处理能力，拥有不同的处理能力的分配策略。
 * 而对于PE对象本身来说，也是这样，它拥有的是PEProvisioner，而不是处理能力
 * 这样将每一部分的处理能和处理能力分配的策略是打包的
 * 
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 2.0
 */
public class PeProvisionerSimple extends PeProvisioner {

	/** 虚拟机ID和分配的处理能力的映射表 The pe table. */
	private Map<String, List<Double>> peTable;

	/**
	 * Creates the PeProvisionerSimple object.
	 * 
	 * @param availableMips the available mips
	 * 
	 * @pre $none
	 * @post $none
	 */
	public PeProvisionerSimple(double availableMips) {
		super(availableMips);
		setPeTable(new HashMap<String, ArrayList<Double>>());
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.PeProvisioner#allocateMipsForVM(cloudsim.power.VM, int)
	 */
	@Override
	public boolean allocateMipsForVm(Vm vm, double mips) {
		return allocateMipsForVm(vm.getUid(), mips);
	}

	/*
	 * (non-Javadoc)给虚拟机分配处理能力
	 * @see cloudsim.provisioners.PeProvisioner#allocateMipsForVm(java.lang.String, double)
	 */
	@Override
	public boolean allocateMipsForVm(String vmUid, double mips) {
		// 可用处理能力比请求需要的处理能力小，请求失败
		if (getAvailableMips() < mips) {
			return false;
		}

		//
		List<Double> allocatedMips;

		if (getPeTable().containsKey(vmUid)) {
			//虚拟机已经分配过处理资源
			allocatedMips = getPeTable().get(vmUid);
		} else {
			allocatedMips = new ArrayList<Double>();
		}
		//继续给虚拟机分配新的处理资源
		allocatedMips.add(mips);

		// 减少PE可用的处理器资源
		setAvailableMips(getAvailableMips() - mips);
		// 将此次分配的处理器资源添加到列表中
		getPeTable().put(vmUid, allocatedMips);

		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.PeProvisioner#allocateMipsForVM(cloudsim.power.VM,
	 * java.util.ArrayList)
	 */
	@Override
	public boolean allocateMipsForVm(Vm vm, List<Double> mips) {
		int totalMipsToAllocate = 0;
		for (double _mips : mips) {
			totalMipsToAllocate += _mips;
		}

		if (getAvailableMips() + getTotalAllocatedMipsForVm(vm) < totalMipsToAllocate) {
			return false;
		}

		setAvailableMips(getAvailableMips() + getTotalAllocatedMipsForVm(vm) - totalMipsToAllocate);

		getPeTable().put(vm.getUid(), mips);

		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.PeProvisioner#deallocateMipsForAllVms()
	 */
	@Override
	public void deallocateMipsForAllVms() {
		super.deallocateMipsForAllVms();
		getPeTable().clear();
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * cloudsim.provisioners.PeProvisioner#getAllocatedMipsForVMByVirtualPeId(cloudsim.power.VM,
	 * int)
	 */
	@Override
	public double getAllocatedMipsForVmByVirtualPeId(Vm vm, int peId) {
		if (getPeTable().containsKey(vm.getUid())) {
			try {
				return getPeTable().get(vm.getUid()).get(peId);
			} catch (Exception e) {
			}
		}
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.PeProvisioner#getAllocatedMipsForVM(cloudsim.power.VM)
	 */
	@Override
	public List<Double> getAllocatedMipsForVm(Vm vm) {
		if (getPeTable().containsKey(vm.getUid())) {
			return getPeTable().get(vm.getUid());
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.PeProvisioner#getTotalAllocatedMipsForVM(cloudsim.power.VM)
	 */
	@Override
	public double getTotalAllocatedMipsForVm(Vm vm) {
		if (getPeTable().containsKey(vm.getUid())) {
			double totalAllocatedMips = 0.0;
			for (double mips : getPeTable().get(vm.getUid())) {
				totalAllocatedMips += mips;
			}
			return totalAllocatedMips;
		}
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.PeProvisioner#deallocateMipsForVM(cloudsim.power.VM)
	 */
	@Override
	public void deallocateMipsForVm(Vm vm) {
		if (getPeTable().containsKey(vm.getUid())) {
			for (double mips : getPeTable().get(vm.getUid())) {
				setAvailableMips(getAvailableMips() + mips);
			}
			getPeTable().remove(vm.getUid());
		}
	}

	/**
	 * Gets the pe table.
	 * 
	 * @return the peTable
	 */
	protected Map<String, List<Double>> getPeTable() {
		return peTable;
	}

	/**
	 * Sets the pe table.
	 * 
	 * @param peTable the peTable to set
	 */
	@SuppressWarnings("unchecked")
	protected void setPeTable(Map<String, ? extends List<Double>> peTable) {
		this.peTable = (Map<String, List<Double>>) peTable;
	}

}
