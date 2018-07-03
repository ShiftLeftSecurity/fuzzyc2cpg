package io.shiftleft.fuzzyc2cpg.cfg.nodes;

import io.shiftleft.fuzzyc2cpg.NodeKeys;
import java.util.Map;

public class InfiniteForNode extends AbstractCFGNode
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
		properties.put(NodeKeys.CODE, "true");
		return properties;
	}
}
