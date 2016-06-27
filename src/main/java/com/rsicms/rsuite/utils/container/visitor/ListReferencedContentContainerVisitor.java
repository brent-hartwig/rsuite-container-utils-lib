package com.rsicms.rsuite.utils.container.visitor;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.ContentAssembly;
import com.reallysi.rsuite.api.ContentAssemblyNodeContainer;
import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.extensions.ExecutionContext;
import com.rsicms.rsuite.helpers.tree.impl.TreeDescendingContentAssemblyVisitorBase;

/**
 * Populate lists of all <code>ContentAssembly</code> and <code>ManagedObject</code> instances
 * directly or indirectly referenced by the starting <code>ContentAssemblyNodeContainer</code>.
 */
public class ListReferencedContentContainerVisitor
    extends TreeDescendingContentAssemblyVisitorBase {

  /**
   * Class log
   */
  @SuppressWarnings("unused")
  private final static Log log = LogFactory.getLog(ListReferencedContentContainerVisitor.class);

  /**
   * The container to begin with.
   */
  private ContentAssemblyNodeContainer startingContainer;

  /**
   * Flag controlling whether only children are visited or also all descendants.
   */
  private boolean visitChildrenOnly;

  /**
   * A complete list of content assemblies that are directly or indirectly referenced by the
   * starting container. This list does not include CANodes.
   */
  private List<ContentAssembly> referencedContentAssemblyList;

  /**
   * A complete list of top-level MOs that are directly or indirectly referenced by the starting
   * container.
   */
  private List<ManagedObject> referencedManagedObjectList;

  /**
   * Construct an instance of this visitor.
   * 
   * @param context
   * @param user
   * @param visitChildrenOnly Submit true to only visit the children of the first container, as
   *        opposed to also its descendants.
   */
  public ListReferencedContentContainerVisitor(ExecutionContext context, User user,
      boolean visitChildrenOnly) {
    super(context, user);
    this.visitChildrenOnly = visitChildrenOnly;
    this.referencedContentAssemblyList = new ArrayList<ContentAssembly>();
    this.referencedManagedObjectList = new ArrayList<ManagedObject>();
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.rsicms.rsuite.helpers.tree.impl. TreeDescendingContentAssemblyVisitorBase#
   * visitContentAssemblyNodeContainer(com.reallysi.rsuite.api. ContentAssemblyNodeContainer)
   */
  @Override
  public void visitContentAssemblyNodeContainer(ContentAssemblyNodeContainer container)
      throws RSuiteException {

    boolean justSetStartingContainer = false;
    if (startingContainer == null) {
      startingContainer = container;
      justSetStartingContainer = true;
    } else if (container instanceof ContentAssembly) {
      referencedContentAssemblyList.add((ContentAssembly) container);
    }

    // Conditionally process the container's contents
    if (justSetStartingContainer || !visitChildrenOnly) {
      super.visitContentAssemblyNodeContainer(container);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.rsicms.rsuite.helpers.tree.impl.DefaultContentAssemblyVisitor#
   * visitManagedObject(com.reallysi .rsuite.api.ManagedObject)
   */
  @Override
  public void visitManagedObject(ManagedObject mo) throws RSuiteException {
    referencedManagedObjectList.add(mo);
  }

  /**
   * @return The starting container.
   */
  public ContentAssemblyNodeContainer getStartingContainer() {
    return startingContainer;
  }

  /**
   * @return True if only allowed to visit children.
   */
  public boolean isVisitChildrenOnly() {
    return visitChildrenOnly;
  }

  /**
   * @return A list of <code>ContentAssembly</code> instances directly or indirectly referenced by
   *         the starting container.
   */
  public List<ContentAssembly> getReferencedContentAssemblies() {
    return referencedContentAssemblyList;
  }

  /**
   * @return A list of <code>ManagedObject</code> instances directory or indirectly referenced by
   *         the starting container.
   */
  public List<ManagedObject> getReferencedManagedObjects() {
    return referencedManagedObjectList;
  }

}
