package io.shiftleft.fuzzyc2cpg.cfg.nodes;

import io.shiftleft.fuzzyc2cpg.JoernNodeKeys;
import java.util.Map;

public class InfiniteForNode extends AbstractCfgNode
{
	@Override
	public String toString()
	{
		return "[INFINITE FOR]";
	}

	@Override
	public Map<String, Object> getProperties()
	{
		Map<String, Object> properties = super.getProperties();
		properties.put(JoernNodeKeys.CODE, "true");
		return properties;
	}
}
