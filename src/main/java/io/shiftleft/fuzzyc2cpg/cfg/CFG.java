package io.shiftleft.fuzzyc2cpg.cfg;


import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgEntryNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgErrorNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgExceptionNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgExitNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgNode;
import io.shiftleft.fuzzyc2cpg.graphutils.IncidenceListGraph;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;

/**
 * Control Flow Graph. Consider this to be the target format of CFGFactories.
 * Please place language specific attributes of the CFG into a sub-class.
 */

public class CFG extends IncidenceListGraph<CfgNode, CFGEdge>
{
	private CfgNode entry;
	private CfgNode exit;
	private CfgNode error;
	private List<CfgNode> parameters;

	private List<CfgNode> breakStatements;
	private List<CfgNode> continueStatements;
	private List<CfgNode> returnStatements;
	private HashMap<CfgNode, String> gotoStatements;
	private HashMap<String, CfgNode> labels;
	private CfgExceptionNode exceptionNode;

	
	public CFG()
	{
		this(new CfgEntryNode(), new CfgExitNode());
	
		setBreakStatements(new LinkedList<CfgNode>());
		setContinueStatements(new LinkedList<CfgNode>());
		setReturnStatements(new LinkedList<CfgNode>());
		setGotoStatements(new HashMap<CfgNode, String>());
		setLabels(new HashMap<String, CfgNode>());
	}

	public CFG(CfgNode entry, CfgNode exit)
	{
		this.entry = entry;
		this.exit = exit;
		addVertex(this.entry);
		addVertex(this.exit);
		parameters = new LinkedList<CfgNode>();
	}

	@Override
	public boolean isEmpty()
	{
		// do not count entry and exit node, since they do not provide any
		// additional information.
		return size() == 2;
	}

	public CfgNode getExitNode()
	{
		return exit;
	}

	public CfgNode getEntryNode()
	{
		return entry;
	}

	public CfgNode getErrorNode()
	{
		if (error == null)
		{
			error = new CfgErrorNode();
			addVertex(error);
		}
		return error;
	}

	public void registerParameter(CfgNode parameter)
	{
		parameters.add(parameter);
	}

	public List<CfgNode> getParameters()
	{
		return parameters;
	}

	public void addCFG(CFG otherCFG)
	{
		addVertices(otherCFG);
		addEdges(otherCFG);
	
		getParameters().addAll(otherCFG.getParameters());
		getBreakStatements().addAll(otherCFG.getBreakStatements());
		getContinueStatements().addAll(otherCFG.getContinueStatements());
		getReturnStatements().addAll(otherCFG.getReturnStatements());
		getGotoStatements().putAll(otherCFG.getGotoStatements());
		getLabels().putAll(otherCFG.getLabels());
		if (this.hasExceptionNode() && otherCFG.hasExceptionNode())
		{
			CfgExceptionNode oldExceptionNode = getExceptionNode();
			CfgExceptionNode newExceptionNode = new CfgExceptionNode();
			setExceptionNode(newExceptionNode);
			addEdge(oldExceptionNode, newExceptionNode,
					CFGEdge.UNHANDLED_EXCEPT_LABEL);
			addEdge(otherCFG.getExceptionNode(), newExceptionNode,
					CFGEdge.UNHANDLED_EXCEPT_LABEL);
		} else if (otherCFG.hasExceptionNode())
		{
			setExceptionNode(otherCFG.getExceptionNode());
		}
	
	}

	public void appendCFG(CFG otherCFG)
	{
		addCFG(otherCFG);
		if (!otherCFG.isEmpty())
		{
			for (CFGEdge edge1 : incomingEdges(getExitNode()))
			{
				for (CFGEdge edge2 : otherCFG
						.outgoingEdges(otherCFG.getEntryNode()))
				{
					addEdge(edge1.getSource(), edge2.getDestination(),
							edge1.getLabel());
				}
			}
			removeEdgesTo(getExitNode());
			for (CFGEdge edge : otherCFG.incomingEdges(otherCFG.getExitNode()))
			{
				addEdge(edge.getSource(), getExitNode(), edge.getLabel());
			}
		}
	}

	public void mountCFG(CfgNode branchNode, CfgNode mergeNode, CFG cfg,
			String label)
	{
		if (!cfg.isEmpty())
		{
			addCFG(cfg);
			for (CFGEdge edge : cfg.outgoingEdges(cfg.getEntryNode()))
			{
				addEdge(branchNode, edge.getDestination(), label);
			}
			for (CFGEdge edge : cfg.incomingEdges(cfg.getExitNode()))
			{
				addEdge(edge.getSource(), mergeNode, edge.getLabel());
			}
		}
		else
		{
			addEdge(branchNode, mergeNode, label);
		}
	}

	private void addVertices(CFG cfg)
	{
		for (CfgNode vertex : cfg.getVertices())
		{
			// do not add entry and exit node
			if (!(vertex.equals(cfg.getEntryNode())
					|| vertex.equals(cfg.getExitNode())))
			{
				addVertex(vertex);
			}
		}
	}

	private void addEdges(CFG cfg)
	{
		for (CfgNode vertex : cfg.getVertices())
		{
			for (CFGEdge edge : cfg.outgoingEdges(vertex))
			{
				if (!(edge.getSource().equals(cfg.getEntryNode())
						|| edge.getDestination().equals(cfg.getExitNode())))
				{
					addEdge(edge);
				}
			}
		}
	}

	public void addEdge(CfgNode srcBlock, CfgNode dstBlock)
	{
		addEdge(srcBlock, dstBlock, CFGEdge.EMPTY_LABEL);
	}

	public void addEdge(CfgNode srcBlock, CfgNode dstBlock, String label)
	{
		CFGEdge edge = new CFGEdge(srcBlock, dstBlock, label);
		addEdge(edge);
	}

	public CFG reverse()
	{
		CFG reverseGraph = new CFG(getExitNode(), getEntryNode());
		for (CfgNode node : getVertices())
		{
			if (!node.equals(getEntryNode()) && !node.equals(getExitNode()))
			{
				reverseGraph.addVertex(node);
			}
		}
		for (CFGEdge edge : getEdges())
		{
			reverseGraph.addEdge(edge.reverse());
		}
		reverseGraph.parameters = parameters;
		return reverseGraph;
	}
	
	public void setExceptionNode(CfgExceptionNode node)
	{
		this.exceptionNode = node;
		addVertex(node);
	}

	public List<CfgNode> getBreakStatements()
	{
		return breakStatements;
	}

	public void setBreakStatements(List<CfgNode> breakStatements)
	{
		this.breakStatements = breakStatements;
	}

	public List<CfgNode> getContinueStatements()
	{
		return continueStatements;
	}

	public void setContinueStatements(List<CfgNode> continueStatements)
	{
		this.continueStatements = continueStatements;
	}

	public HashMap<String, CfgNode> getLabels()
	{
		return labels;
	}

	public void setLabels(HashMap<String, CfgNode> labels)
	{
		this.labels = labels;
	}

	public HashMap<CfgNode, String> getGotoStatements()
	{
		return gotoStatements;
	}

	public void setGotoStatements(HashMap<CfgNode, String> gotoStatements)
	{
		this.gotoStatements = gotoStatements;
	}

	public List<CfgNode> getReturnStatements()
	{
		return returnStatements;
	}

	public void setReturnStatements(List<CfgNode> returnStatements)
	{
		this.returnStatements = returnStatements;
	}

	public void addBlockLabel(String label, CfgNode block)
	{
		getLabels().put(label, block);
	}

	public void addBreakStatement(CfgNode statement)
	{
		getBreakStatements().add(statement);
	}

	public void addContinueStatement(CfgNode statement)
	{
		getContinueStatements().add(statement);
	}

	public void addGotoStatement(CfgNode gotoStatement, String gotoTarget)
	{
		getGotoStatements().put(gotoStatement, gotoTarget);
	}

	public void addReturnStatement(CfgNode returnStatement)
	{
		getReturnStatements().add(returnStatement);
	}

	public CfgNode getBlockByLabel(String label)
	{
		CfgNode block = getLabels().get(label);
		if (block == null)
		{
			System.err
					.println("warning : can not find block for label " + label);
			return getErrorNode();
		}
		return block;
	}

	public CfgExceptionNode getExceptionNode()
	{
		return this.exceptionNode;
	}

	public boolean hasExceptionNode()
	{
		return this.exceptionNode != null;
	}
	

}
