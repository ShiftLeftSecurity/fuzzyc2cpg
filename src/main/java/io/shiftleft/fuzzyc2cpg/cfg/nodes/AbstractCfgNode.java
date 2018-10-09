package io.shiftleft.fuzzyc2cpg.cfg.nodes;

import io.shiftleft.fuzzyc2cpg.JoernNodeKeys;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractCfgNode implements CfgNode
{
	private Long id = -1l;

	public Long getNodeId() {
		return this.id;
	}

	public void setNodeId( Long id) {
		this.id = id;
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName();
	}

	@Override
	public Map<String, Object> getProperties()
	{
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(JoernNodeKeys.CODE, toString());
		properties.put(JoernNodeKeys.NODE_TYPE, getClass().getSimpleName());
		properties.put(JoernNodeKeys.IS_CFG_NODE, "True");
		return properties;
	}

}
