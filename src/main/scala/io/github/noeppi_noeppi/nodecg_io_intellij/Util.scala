package io.github.noeppi_noeppi.nodecg_io_intellij

import java.awt.Component
import java.awt.event.{InputEvent, KeyEvent}
import java.lang.reflect.Method
import java.math.{BigDecimal, BigInteger}

import com.google.gson._
import com.intellij.execution.{RunManager, RunnerAndConfigurationSettings}
import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.core.tree.RootEntry
import com.intellij.history.core.{LabelImpl, LocalHistoryFacade}
import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.history.{ByteContent, LocalHistory}
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.actionSystem.ex.{ActionManagerEx, ActionUtil}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager

import scala.jdk.javaapi.CollectionConverters

object Util {

  private lazy val revertMethod: Method = {
    val rm = classOf[LocalHistoryImpl].getDeclaredMethod("revertToLabel", classOf[Project], classOf[VirtualFile], classOf[LabelImpl])
    rm.setAccessible(true)
    rm
  }

  def getActiveProject: Project = {
    val pmanager = ProjectManager.getInstance
    val wmanager = WindowManager.getInstance
    if (pmanager == null || wmanager == null) return null
    val projs = pmanager.getOpenProjects
    for (proj <- projs) {
      if (proj.isOpen && !proj.isDisposed) {
        val frame = wmanager.getFrame(proj)
        if (frame != null && frame.isActive)
          return proj
      }
    }
    null
  }

  def getNamedProject(project: String): Project = {
    val pmanager = ProjectManager.getInstance
    if (pmanager == null) return null
    val projs = pmanager.getOpenProjects
    for (proj <- projs) {
      if (proj.isOpen && !proj.isDisposed) {
        if (proj.getName.equalsIgnoreCase(project))
         return proj
      }
    }
    null
  }

  def getLhLabels: List[ChangeSet] = {
    val lh = LocalHistory.getInstance().asInstanceOf[LocalHistoryImpl]
    val facade = lh.getFacade
    filterLh(facade, change => change.getLabel != null).sortBy(change => change.getTimestamp).reverse
  }

  def getLhChanges(vf: VirtualFile): List[ChangeSet] = {
    val lh = LocalHistory.getInstance().asInstanceOf[LocalHistoryImpl]
    val facade = lh.getFacade
    filterLh(facade, change => !change.isLabelOnly && (vf == null || change.affectsPath(vf.getPath))).sortBy(change => change.getTimestamp).reverse
  }

  def ensureLhChange(id: Long): ChangeSet = {
    val lh = LocalHistory.getInstance().asInstanceOf[LocalHistoryImpl]
    val facade = lh.getFacade
    filterLh(facade, change => change.getId == id).headOption.getOrElse(throw new IllegalStateException("Change not found"))
  }

  def revertChangeSet(project: Project, file: VirtualFile, lh: LocalHistoryImpl, change: ChangeSet): Unit = {
    val root = new RootEntry
    val newPath = lh.getFacade.revertUpTo(root, file.getPath, change, null, true, false)
    val entry = root.findEntry(newPath)
    val limpl = new LabelImpl() {
      override def getLabelChangeId: Long = change.getFirstChange.getId
      override def getByteContent(root: RootEntry, path: String): ByteContent = new ByteContent(file.isDirectory, entry.getContent.getBytesIfAvailable)
    }
    revertMethod.invoke(lh, project, file, limpl)
  }

  def getBytes(file: VirtualFile, lh: LocalHistoryImpl, change: ChangeSet): Array[Byte] = {
    val root = new RootEntry
    val newPath = lh.getFacade.revertUpTo(root, file.getPath, change, null, true, false)
    val entry = root.findEntry(newPath)
    if (entry == null)
      return null
    entry.getContent.getBytesIfAvailable
  }

  private def filterLh(facade: LocalHistoryFacade, filter: ChangeSet => Boolean): List[ChangeSet] = {
    if (facade == null)
      throw new IllegalStateException("No history facade")
    CollectionConverters.asScala(facade.getChangeListInTests.iterChanges()).filter(filter).toList
  }

  def ensureRunConfiguration(rm: RunManager, unique: String): RunnerAndConfigurationSettings = {
    CollectionConverters.asScala(rm.getAllSettings).find(configuration => configuration.getUniqueID == unique).getOrElse(throw new IllegalStateException("Configuration not found."))
  }

  def getFirstOpenFile(project: Project): VirtualFile = {
    val selected = FileEditorManager.getInstance(project).getSelectedEditor()
    if (selected == null)
      return null
    selected.getFile
  }

  def runAction(action: AnAction, place: String, sync: Boolean, project: Project): Unit = {
    val runAction: Runnable = () => {
      val ctx = new DataContext {
        override def getData(dataId: String): AnyRef = if (dataId == CommonDataKeys.PROJECT.getName) project else null
      }
      val event = new AnActionEvent(newFakeInputEvent(), ctx, if (place != null) place else ActionPlaces.UNKNOWN, new Presentation, ActionManagerEx.getInstanceEx, 0)

      ActionManagerEx.getInstanceEx.fireBeforeActionPerformed(action, ctx, event)
      ActionUtil.performActionDumbAware(action, event)
      ActionManagerEx.getInstanceEx.fireAfterActionPerformed(action, ctx, event)
    }

    if (sync) {
      ApplicationManager.getApplication.invokeAndWait(runAction)
    } else {
      ApplicationManager.getApplication.invokeLater(runAction)
    }
  }

  def newFakeInputEvent(): InputEvent = {
    new KeyEvent(new Component() {}, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_UNKNOWN)
  }

  object Implicits {

    implicit val gson: Gson = new GsonBuilder().serializeNulls.create

    implicit class ImplicitJsonElement(val elem: JsonElement) {

      def optionJsonObject: Option[JsonObject] = if (elem.isJsonObject) Some(elem.getAsJsonObject) else None
      def optionJsonArray: Option[JsonArray] = if (elem.isJsonArray) Some(elem.getAsJsonArray) else None
      def optionJsonPrimitive: Option[JsonPrimitive] = if (elem.isJsonPrimitive) Some(elem.getAsJsonPrimitive) else None
      def optionJsonNull: Option[JsonNull] = if (elem.isJsonNull) Some(elem.getAsJsonNull) else None
      def optionBoolean: Option[Boolean] = optionJsonPrimitive.flatMap(p => if (p.isBoolean) Some(p.getAsBoolean) else None)
      def optionNumber: Option[Number] = optionJsonPrimitive.flatMap(p => if (p.isNumber) Some(p.getAsNumber) else None)
      def optionString: Option[String] = optionJsonPrimitive.flatMap(p => if (p.isString) Some(p.getAsString) else None)
      def optionDouble: Option[Double] = try { Some(elem.getAsDouble) } catch { case _: Exception => None}
      def optionFloat: Option[Float] = try { Some(elem.getAsFloat) } catch { case _: Exception => None}
      def optionLong: Option[Long] = try { Some(elem.getAsLong) } catch { case _: Exception => None}
      def optionInt: Option[Int] = try { Some(elem.getAsInt) } catch { case _: Exception => None}
      def optionByte: Option[Byte] = try { Some(elem.getAsByte) } catch { case _: Exception => None}
      def optionBigDecimal: Option[BigDecimal] = try { Some(elem.getAsBigDecimal) } catch { case _: Exception => None}
      def optionBigInteger: Option[BigInteger] = try { Some(elem.getAsBigInteger) } catch { case _: Exception => None}
      def optionShort: Option[Short] = try { Some(elem.getAsShort) } catch { case _: Exception => None}

      def nullableJsonObject: JsonObject = if (elem.isJsonNull) null else elem.getAsJsonObject
      def nullableJsonArray: JsonArray = if (elem.isJsonNull) null else elem.getAsJsonArray
      def nullableJsonPrimitive: JsonPrimitive = if (elem.isJsonNull) null else elem.getAsJsonPrimitive
      def nullableJsonNull: JsonNull = if (elem.isJsonNull) null else elem.getAsJsonNull
      def nullableNumber: Number = if (elem.isJsonNull) null else elem.getAsNumber
      def nullableString: String = if (elem.isJsonNull) null else elem.getAsString
      def nullableBigDecimal: BigDecimal = if (elem.isJsonNull) null else elem.getAsBigDecimal
      def nullableBigInteger: BigInteger = if (elem.isJsonNull) null else elem.getAsBigInteger

      def getAsBigInt: BigInt = BigInt(elem.getAsBigInteger)
      def optionBigInt: Option[BigInt] = optionBigInteger.map(bi => BigInt(bi))
      def nullableBigInt: BigInt = if (elem.isJsonNull) null else getAsBigInt
    }
  }
}
