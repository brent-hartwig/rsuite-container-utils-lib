package com.rsicms.rsuite.utils.container.visitor;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.ContentAssemblyItem;
import com.reallysi.rsuite.api.ContentAssemblyNode;
import com.reallysi.rsuite.api.ContentAssemblyNodeContainer;
import com.reallysi.rsuite.api.ContentAssemblyReference;
import com.reallysi.rsuite.api.ManagedObjectReference;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.extensions.ExecutionContext;
import com.rsicms.rsuite.helpers.tree.impl.TreeDescendingContentAssemblyVisitorBase;

/**
 * Collects info on the container children (excludes descendants) and provides some
 * "sibling navigation" convenience methods/logic for the visited container's children.
 */
public class ChildrenInfoContainerVisitor extends TreeDescendingContentAssemblyVisitorBase {

  @SuppressWarnings("unused")
  private static Log log = LogFactory.getLog(ChildrenInfoContainerVisitor.class);

  /**
   * The container to begin with.
   */
  private ContentAssemblyNodeContainer startingContainer;

  private List<ChildObject> children;

  public ChildrenInfoContainerVisitor(ExecutionContext context, User user) {
    super(context, user);
    children = new ArrayList<ChildObject>();
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

    if (startingContainer == null) {
      startingContainer = container;
      super.visitContentAssemblyNodeContainer(container);
    }

  }

  @Override
  public void visitContentAssemblyNode(ContentAssemblyNode caNode) throws RSuiteException {
    children.add(new ChildObject(caNode));
  }

  @Override
  public void visitDynamicContentAssembly(ContentAssemblyNode caNode) throws RSuiteException {
    children.add(new ChildObject(caNode));
  }

  @Override
  public void visitContentAssemblyReference(ContentAssemblyReference containerRef)
      throws RSuiteException {
    children.add(new ChildObject(containerRef));
  }

  @Override
  public void visitManagedObjectReference(ManagedObjectReference moRef) throws RSuiteException {
    children.add(new ChildObject(moRef));
  }

  /**
   * Get the immediately preceding sibling to the specified object within this visited container.
   * 
   * @param id ID of object to return the preceding sibling of.
   * @return Any object that is also a child of the visited container that comes immediately before
   *         the identified object. May be null.
   * @throws RSuiteException Thrown if the provided ID does not identify a child of the visited
   *         container.
   */
  public ContentAssemblyItem getAnySiblingBefore(String id) throws RSuiteException {
    return getSibling(ContentAssemblyItem.class, true, id);
  }

  /**
   * Get the immediately following sibling to the specified object within this visited container.
   * 
   * @param id ID of object to return the following sibling of.
   * @return Any object that is also a child of the visited container that comes immediately after
   *         the identified object. May be null.
   * @throws RSuiteException Thrown if the provided ID does not identify a child of the visited
   *         container.
   */
  public ContentAssemblyItem getAnySiblingAfter(String id) throws RSuiteException {
    return getSibling(ContentAssemblyItem.class, false, id);
  }

  /**
   * Get the immediately preceding MO reference to the specified object within this visited
   * container.
   * 
   * @param id ID of object to return the preceding sibling MO reference of.
   * @return When there is one, the MO reference that immediately precedes the specified one. May be
   *         null.
   * @throws RSuiteException Thrown if the provided ID does not identify a child of the visited
   *         container.
   */
  public ManagedObjectReference getMoRefSiblingBefore(String id) throws RSuiteException {
    return (ManagedObjectReference) getSibling(ManagedObjectReference.class, true, id);
  }

  /**
   * Get the immediately following MO reference to the specified object within this visited
   * container.
   * 
   * @param id ID of object to return the following sibling MO reference of.
   * @return When there is one, the MO reference that immediately follows the specified one. May be
   *         null.
   * @throws RSuiteException Thrown if the provided ID does not identify a child of the visited
   *         container.
   */
  public ManagedObjectReference getMoRefSiblingAfter(String id) throws RSuiteException {
    return (ManagedObjectReference) getSibling(ManagedObjectReference.class, false, id);
  }

  /**
   * Underlying logic to find the preceding or following sibling of a specified class.
   * 
   * @param qualifyingClass The class the sibling must have to qualify.
   * @param before Submit true for the previous sibling or false for the following.
   * @param id ID of object to return the sibling of.
   * @return Qualifying sibling, or null when there isn't one.
   * @throws RSuiteException
   */
  protected ContentAssemblyItem getSibling(Class<? extends ContentAssemblyItem> qualifyingClass,
      boolean before, String id) throws RSuiteException {
    ContentAssemblyItem candidateItem = null;
    ChildObject child;

    int loopCnt = 0;
    int stopAt = before ? children.size() : -1;
    int loopIncrement = before ? 1 : -1;

    for (int i = before ? 0 : children.size() - 1; i != stopAt; i = i + loopIncrement) {
      // protect from infinite loop
      if (++loopCnt > children.size()) {
        throw new RSuiteException(RSuiteException.ERROR_INTERNAL_ERROR,
            "Avoiding infinite loop; invalid loop logic.");
      }

      child = children.get(i);
      // had trouble getting instanceof working.
      if (child.hasId(id) && qualifyingClass.isAssignableFrom(child.caItem.getClass())) {
        return candidateItem;
      }
      candidateItem = child.caItem;
    }
    throw new RSuiteException(RSuiteException.ERROR_PARAM_INVALID,
        new StringBuilder(id).append(" does not identify a child in the '")
            .append(startingContainer.getDisplayName()).append("' (ID: ")
            .append(startingContainer.getId())
            .append(") container; unable to retrieve a sibling thereof.").toString());
  }

  /**
   * Get one of the visited container's child MO references by either of its IDs.
   * 
   * @param id ID to match on.
   * @return The matching MO ref.
   * @throws RSuiteException Thrown if the container doesn't have such an MO ref.
   */
  public ManagedObjectReference getMoRef(String id) throws RSuiteException {
    for (ChildObject child : children) {
      if (child.hasId(id) && child.caItem instanceof ManagedObjectReference) {
        return (ManagedObjectReference) child.caItem;
      }
    }
    throw new RSuiteException(RSuiteException.ERROR_PARAM_INVALID,
        new StringBuilder(id).append(" does not identify an MO reference in the '")
            .append(startingContainer.getDisplayName()).append("' (ID: ")
            .append(startingContainer.getId()).append(") container.").toString());
  }

  /**
   * A child of the visited container. Supports an ID comparison that will match on the object's ID
   * as well as that of the object it references (when a reference).
   */
  protected class ChildObject {

    private ContentAssemblyItem caItem;
    private String refId;
    private String targetId;

    protected ChildObject(ContentAssemblyNode caNode) {
      this.caItem = caNode;
      this.targetId = caNode.getId();
    }

    protected ChildObject(ContentAssemblyReference caRef) throws RSuiteException {
      this.caItem = caRef;
      this.refId = caRef.getId();
      this.targetId = caRef.getTargetId();
    }

    protected ChildObject(ManagedObjectReference moRef) throws RSuiteException {
      this.caItem = moRef;
      this.refId = moRef.getId();
      this.targetId = moRef.getTargetId();
    }

    /**
     * @param id ID to compare.
     * @return True if given ID matches the child object's ID or, when a reference, the ID of the
     *         object it references.
     */
    protected boolean hasId(String id) {
      return (refId != null && refId.equals(id)) || (targetId != null && targetId.equals(id));
    }
  }

}
