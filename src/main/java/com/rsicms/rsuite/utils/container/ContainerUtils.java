package com.rsicms.rsuite.utils.container;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.ContentAssembly;
import com.reallysi.rsuite.api.ContentAssemblyNodeContainer;
import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.MetaDataItem;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.control.ObjectDestroyOptions;
import com.reallysi.rsuite.api.extensions.ExecutionContext;
import com.reallysi.rsuite.service.ManagedObjectService;
import com.rsicms.rsuite.utils.container.visitor.ListReferencedContentContainerVisitor;
import com.rsicms.rsuite.utils.mo.MOUtils;
import com.rsicms.rsuite.utils.mo.qualifiers.ManagedObjectQualifier;
import com.rsicms.rsuite.utils.operation.result.BaseOperationResult;
import com.rsicms.rsuite.utils.operation.result.OperationResult;

/**
 * A collection of container utility methods.
 */
public class ContainerUtils {

  private final static Log log = LogFactory.getLog(ContainerUtils.class);

  /**
   * Get an LMD value by LMD name from the specified container. Should there be multiple LMD values
   * with the same LMD name, the first one, as decided by the RSuite API, will be returned.
   * 
   * @param container
   * @param lmdName
   * @return LMD value or null when the requested LMD isn't set on the specified container.
   * @throws RSuiteException
   */
  public static String getLayeredMetadataValue(ContentAssemblyNodeContainer container,
      String lmdName) throws RSuiteException {
    if (container != null && container.getMetaDataItems() != null
        && StringUtils.isNotBlank(lmdName)) {
      for (MetaDataItem item : container.getMetaDataItems()) {
        if (lmdName.equalsIgnoreCase(item.getName())) {
          return item.getValue();
        }
      }
    }
    return null;
  }

  /**
   * DANGER: This method permanently deletes the given container and EVERYTHING it references, even
   * if also referenced by other containers.
   * <p>
   * This implementation minimizes the number of database writes by deleting containers before MOs.
   * <p>
   * There is no code within that explicitly deals with CANodes as those are deleted with the CAs.
   * <p>
   * Error handling could be added in an attempt to continue despite having an issue with one
   * object.
   * 
   * @param context
   * @param user The user to operate as. User must be an administrator.
   * @param container The container to permanently deleted, as well as all content it references.
   * @throws RSuiteException Thrown if unable to complete the operation successfully. A possible
   *         outcome is that some of the objects were destroyed, but not all.
   */
  public static OperationResult deleteContainerAndReferencedContent(ExecutionContext context,
      User user, ContentAssemblyNodeContainer container, Log log) throws RSuiteException {

    OperationResult result = new BaseOperationResult(context.getIDGenerator().allocateId(),
        "delete", log == null ? ContainerUtils.log : log);
    result.markStartOfOperation();

    result.addInfoMessage(ContainerUtilsMessageProperties.get(
        "info.received.request.to.delete.container.and.its.contents", user.getUserId(),
        container.getDisplayName(), container.getId()));

    // Require user is an administrator.
    if (!context.getAuthorizationService().isAdministrator(user)) {
      result.addFailure(new RSuiteException(RSuiteException.ERROR_PERMISSION_DENIED,
          ContainerUtilsMessageProperties.get("security.error.operation.restricted.to.admins")));
      return result;
    }

    // Visit the container
    ListReferencedContentContainerVisitor visitor =
        new ListReferencedContentContainerVisitor(context, user, false);
    visitor.visitContentAssemblyNodeContainer(container);

    ManagedObjectService moService = context.getManagedObjectService();
    ObjectDestroyOptions options = new ObjectDestroyOptions();

    // 1st: delete the starting container.
    if (visitor.getStartingContainer() != null) {
      result.addInfoMessage(ContainerUtilsMessageProperties.get("info.deleting.object",
          visitor.getStartingContainer().getDisplayName(), visitor.getStartingContainer().getId()));
      deleteContainer(context, user, visitor.getStartingContainer(), log);
    }

    /*
     * 2nd: delete the CAs, in the list's order. Those that were directly referenced by the starting
     * container are no longer referenced by it. The idea is that those sooner in the list may
     * reference those later in the list (and not the other way around).
     */
    if (visitor.getReferencedContentAssemblies() != null) {
      for (ContentAssembly ca : visitor.getReferencedContentAssemblies()) {
        result.addInfoMessage(ContainerUtilsMessageProperties.get("info.deleting.object",
            ca.getDisplayName(), ca.getId()));
        deleteContainer(context, user, ca, log);
      }
    }

    /*
     * 3rd: delete the MOs, which are no longer referenced by the containers deleted above.
     */
    if (visitor.getReferencedManagedObjects() != null) {
      for (ManagedObject mo : visitor.getReferencedManagedObjects()) {
        result.addInfoMessage(ContainerUtilsMessageProperties.get("info.deleting.object",
            mo.getDisplayName(), mo.getId()));
        try {
          MOUtils.checkout(context, user, mo.getId());
          moService.destroy(user, mo.getId(), options);
        } catch (RSuiteException e) {
          result.addWarning(e);
        }
      }
    }

    result.setDestroyedContentAssemblies(visitor.getReferencedContentAssemblies());
    result.setDestroyedManagedObjects(visitor.getReferencedManagedObjects());
    result.markEndOfOperation();
    return result;
  }

  /**
   * Delete a content assembly node container, regardless of it being a CA or CANode.
   * <p>
   * This method does not delete any content the container references.
   * 
   * @param context
   * @param user
   * @param container
   * @param log
   * @throws RSuiteException
   */
  public static void deleteContainer(ExecutionContext context, User user,
      ContentAssemblyNodeContainer container, Log log) throws RSuiteException {
    if (container instanceof ContentAssembly) {
      context.getContentAssemblyService().removeContentAssembly(user, container.getId());
    } else {
      context.getContentAssemblyService().deleteCANode(user, container.getId());
    }
  }

  /**
   * Rename a CA or CANode.
   * 
   * @param context
   * @param user
   * @param container
   * @param name New name for the container.
   * @return True if it was necessary to rename the container and the rename was performed. When the
   *         rename is not necessary, the method doesn't bother trying and returns false.
   * @throws RSuiteException
   */
  public static boolean renameContainer(ExecutionContext context, User user,
      ContentAssemblyNodeContainer container, String name) throws RSuiteException {
    // Only proceed if we were given a container, the name isn't blank, and
    // the name is different.
    if (container != null && StringUtils.isNotBlank(name)
        && !name.equals(container.getDisplayName())) {
      if (container instanceof ContentAssembly) {
        context.getContentAssemblyService().renameContentAssembly(user, container.getId(), name);
      } else {
        context.getContentAssemblyService().renameCANode(user, container.getId(), name);
      }
      return true;
    }
    return false;
  }

  /**
   * Get the first qualifying MO directly or indirectly referenced by the provided container. The
   * provided MO qualifier provides the logic of which MOs qualify.
   * <p>
   * The implementation defines "first".
   * <p>
   * If the caller needs a list of all qualifying MOs, more work would be required.
   * 
   * @param context
   * @param user
   * @param container
   * @param moQualifier
   * @return The first qualifying MO, or null when there isn't a qualifying MO.
   * @throws RSuiteException
   */
  public static ManagedObject getFirstQualifyingReferencedManagedObject(ExecutionContext context,
      User user, ContentAssemblyNodeContainer container, ManagedObjectQualifier moQualifier)
      throws RSuiteException {

    if (container != null && moQualifier != null) {
      /*
       * IDEA: Stop processing the container as soon as the first qualifying MO is found. Elected
       * not to do this initially as this visitor already existed and the project containers are not
       * expected to reference many objects.
       */
      ListReferencedContentContainerVisitor visitor =
          new ListReferencedContentContainerVisitor(context, user, false);
      visitor.visitContentAssemblyNodeContainer(container);

      List<ManagedObject> mos = visitor.getReferencedManagedObjects();
      if (mos != null) {
        for (ManagedObject mo : mos) {
          if (moQualifier.accept(mo)) {
            return mo;
          }
        }
      }
    }

    return null;
  }

  /**
   * Get the managed object referenced by the given container that comes before or after the
   * identified child, depending on the value of the "previous" parameter.
   * 
   * @param context
   * @param user
   * @param container Container to look within.
   * @param childId ID of an object that the container already (directly) references. May be the
   *        reference or target ID.
   * @param previous Submit true for the previous sibling or false for the next sibling.
   * @return The managed object that comes before or after the specified child. Returns null when
   *         there isn't a previous or next child, including when the container does not reference
   *         the specified child. Containers are not returned.
   * @throws RSuiteException
   */
  public static ManagedObject getSiblingManagedObject(ExecutionContext context, User user,
      ContentAssemblyNodeContainer container, String childId, boolean previous)
      throws RSuiteException {
    ListReferencedContentContainerVisitor visitor =
        new ListReferencedContentContainerVisitor(context, user, true);
    visitor.visitContentAssemblyNodeContainer(container);
    List<ManagedObject> moList = visitor.getReferencedManagedObjects();
    for (int i = 0; i < moList.size(); i++) {
      if (moList.get(i).getId().equals(childId)) {
        if (previous) {
          return i > 0 ? moList.get(i - 1) : null;
        } else {
          return i < moList.size() - 1 ? moList.get(i + 1) : null;
        }
      }
    }
    return null;
  }

}
