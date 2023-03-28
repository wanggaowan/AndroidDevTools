package com.wanggaowan.android.dev.tools.ui

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase
import com.intellij.ide.projectView.impl.ProjectTreeBuilder
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.TreeFileChooser
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.wanggaowan.android.dev.tools.listener.SimpleComponentListener
import com.wanggaowan.android.dev.tools.utils.Toast
import java.awt.*
import java.awt.event.ComponentEvent
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel


private object Config {
    var isDarkTheme = false

    private val LINE_COLOR = Color(209, 209, 209)
    private val LINE_COLOR_DARK = Color(50, 50, 50)

    private val MOUSE_ENTER_COLOR = Color(223, 223, 223)
    private val MOUSE_ENTER_COLOR_DARK = Color(76, 80, 82)

    // 用于透明Icon使用
    private val MOUSE_ENTER_COLOR2 = Color(191, 197, 200)
    private val MOUSE_ENTER_COLOR_DARK2 = Color(98, 106, 110)

    private val MOUSE_PRESS_COLOR = Color(207, 207, 207)
    private val MOUSE_PRESS_COLOR_DARK = Color(92, 97, 100)

    private val INPUT_FOCUS_COLOR = Color(71, 135, 201)

    private val INPUT_UN_FOCUS_COLOR = Color(196, 196, 196)
    private val INPUT_UN_FOCUS_COLOR_DARK = Color(100, 100, 100)

    private val IMAGE_TITLE_BG_COLOR = Color(252, 252, 252)
    private val IMAGE_TITLE_BG_COLOR_DARK = Color(49, 52, 53)

    val TRANSPARENT = Color(0, 0, 0, 0)

    fun getLineColor(): Color {
        if (isDarkTheme) {
            return LINE_COLOR_DARK
        }

        return LINE_COLOR
    }

    fun getMouseEnterColor(): Color {
        if (isDarkTheme) {
            return MOUSE_ENTER_COLOR_DARK
        }

        return MOUSE_ENTER_COLOR
    }

    fun getMouseEnterColor2(): Color {
        if (isDarkTheme) {
            return MOUSE_ENTER_COLOR_DARK2
        }

        return MOUSE_ENTER_COLOR2
    }

    fun getMousePressColor(): Color {
        if (isDarkTheme) {
            return MOUSE_PRESS_COLOR_DARK
        }

        return MOUSE_PRESS_COLOR
    }

    fun getInputFocusColor(): Color {
        if (isDarkTheme) {
            return INPUT_FOCUS_COLOR
        }

        return INPUT_FOCUS_COLOR
    }

    fun getInputUnFocusColor(): Color {
        if (isDarkTheme) {
            return INPUT_UN_FOCUS_COLOR_DARK
        }

        return INPUT_UN_FOCUS_COLOR
    }

    fun getImageTitleBgColor(): Color {
        if (isDarkTheme) {
            return IMAGE_TITLE_BG_COLOR_DARK
        }

        return IMAGE_TITLE_BG_COLOR
    }
}

/**
 * 导入图片资源后选择导入的目标文件夹弹窗，兼带重命名导入文件名称功能
 *
 * @author Created by wanggaowan on 2022/7/18 08:37
 */
class ImportImageFolderChooser(
    val project: Project,
    title: String,
    initialFile: VirtualFile? = null,
    /**
     * 需要重命名的文件
     */
    renameFiles: List<VirtualFile>? = null,
    /**
     * 文件夹过滤器
     */
    private val filter: TreeFileChooser.PsiFileFilter? = null
) : JDialog() {
    private lateinit var myTree: Tree
    private lateinit var mBtnOk: JButton
    private lateinit var mJChosenFolder: JLabel

    /**
     * 切换文件选择/重命名面板的父面板
     */
    private var mCardPane: JPanel

    /**
     * 选中的文件夹
     */
    private var mSelectedFolder: VirtualFile? = null

    private var mBuilder: ProjectTreeBuilder? = null
    private val mDisableStructureProviders = false
    private val mShowLibraryContents = false
    private var mCardShow = CARD_RENAME
    private var mRenameFileMap = mutableMapOf<String, MutableList<RenameEntity>>()

    /**
     * 确定按钮点击监听
     */
    private var mOkActionListener: (() -> Unit)? = null

    init {
        val isDarkTheme = ColorUtil.isDark(background)
        Config.isDarkTheme = isDarkTheme
        mSelectedFolder = initialFile

        isAlwaysOnTop = true
        setTitle(title)

        val rootPanel = JPanel(BorderLayout())
        contentPane = rootPanel
        val layout = CardLayout()
        mCardPane = JPanel(layout)
        mCardPane.add(createRenameFilePanel(renameFiles), CARD_RENAME)
        mCardPane.add(createFileChoosePanel(), CARD_FILE)
        rootPanel.add(mCardPane, BorderLayout.CENTER)
        rootPanel.add(createAction(), BorderLayout.SOUTH)
        pack()

        if (initialFile != null) {
            // dialog does not exist yet
            SwingUtilities.invokeLater { selectFolder(initialFile) }
            mBtnOk.isEnabled = true
            mJChosenFolder.text = initialFile.path.replace(project.basePath ?: "", "")
        } else {
            mBtnOk.isEnabled = false
        }
    }

    override fun setVisible(b: Boolean) {
        if (b) {
            val window = WindowManager.getInstance().suggestParentWindow(project)
            window?.let {
                location = Point(it.x + (it.width - this.width) / 2, it.y + (it.height - this.height) / 2)
            }
        }
        super.setVisible(b)
    }

    /**
     * 构建文件选择面板
     */
    private fun createFileChoosePanel(): JComponent {
        val model = DefaultTreeModel(DefaultMutableTreeNode())
        myTree = Tree(model)
        val treeStructure: ProjectAbstractTreeStructureBase = object : AbstractProjectTreeStructure(project) {
            override fun isHideEmptyMiddlePackages(): Boolean {
                return true
            }

            override fun getChildElements(element: Any): Array<Any> {
                return filterFiles(super.getChildElements(element))
            }

            override fun isShowLibraryContents(): Boolean {
                return mShowLibraryContents
            }

            override fun isShowModules(): Boolean {
                return false
            }

            override fun getProviders(): List<TreeStructureProvider>? {
                return if (mDisableStructureProviders) null else super.getProviders()
            }
        }

        mBuilder = ProjectTreeBuilder(project, myTree, model, AlphaComparator.INSTANCE, treeStructure)
        myTree.isRootVisible = false
        myTree.expandRow(0)
        myTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        myTree.cellRenderer = NodeRenderer()
        val scrollPane = ScrollPaneFactory.createScrollPane(myTree)
        scrollPane.preferredSize = JBUI.size(600, 300)
        myTree.addTreeSelectionListener { handleSelectionChanged() }
        return scrollPane
    }

    /**
     * 构建重命名文件面板
     */
    private fun createRenameFilePanel(files: List<VirtualFile>?): JComponent {
        mRenameFileMap.clear()
        val rootPane = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))

        files?.forEach {
            val parentName = if (it.path.contains("drawable")) "Drawable"
            else if (it.path.contains("mipmap")) {
                "Mipmap"
            } else {
                ""
            }

            var list = mRenameFileMap[parentName]
            if (list == null) {
                list = mutableListOf()
                mRenameFileMap[parentName] = list
            }

            list.add(RenameEntity(it.name, it.name))
        }

        var totalHeight = 0
        val isDarkTheme = ColorUtil.isDark(background)
        mRenameFileMap.forEach {
            val type = JLabel(it.key + "：")
            type.preferredSize = JBUI.size(600, 40)
            type.border = BorderFactory.createEmptyBorder(0, 5, 5, 5)
            val fontSize = (UIUtil.getFontSize(UIUtil.FontSize.NORMAL) + 2).toInt()
            type.font = Font(type.font.name, Font.BOLD, fontSize)
            rootPane.add(type)
            totalHeight += 40

            it.value.forEach { it2 ->
                val panel = JPanel(BorderLayout())
                panel.border = BorderFactory.createEmptyBorder(0, 5, 5, 5)
                totalHeight += 40

                val titleBox = Box.createHorizontalBox()
                val imageFile = getFile(files, it.key == "Drawable", it2.oldName)
                if (imageFile != null) {
                    val imageView = ImageView(imageFile, isDarkTheme)
                    imageView.preferredSize = JBUI.size(35)
                    imageView.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
                    imageView.maximumSize = JBUI.size(35)
                    titleBox.add(imageView)
                }

                val title = JLabel(it2.oldName + "：")
                title.preferredSize = JBUI.size(200, 35)
                titleBox.add(title)
                panel.add(titleBox, BorderLayout.WEST)

                val box = Box.createVerticalBox()
                panel.add(box, BorderLayout.CENTER)

                val rename = JTextField(it2.newName)
                rename.preferredSize = JBUI.size(350, 35)
                box.add(rename)


                val box2 = Box.createHorizontalBox()

                val existFile = isImageExist(it.key == "Drawable", it2.newName)
                val existFileImageView = ImageView(if (existFile != null) File(existFile.path) else null, isDarkTheme)
                existFileImageView.preferredSize = JBUI.size(25)
                existFileImageView.border = BorderFactory.createEmptyBorder(2, 0, 2, 5)
                existFileImageView.isVisible = existFile != null
                existFileImageView.maximumSize = JBUI.size(25)
                box2.add(existFileImageView)

                val hint = JCheckBox("已存在同名文件,是否覆盖原文件？不勾选则跳过导入")
                hint.foreground = JBColor.RED
                hint.font = UIUtil.getFont(UIUtil.FontSize.MINI, rename.font)
                it2.existFile = existFile != null
                hint.isVisible = existFile != null
                box2.add(hint)

                box2.add(Box.createHorizontalGlue())
                box.add(box2)

                rootPane.add(panel)

                hint.addChangeListener {
                    it2.coverExistFile = hint.isSelected
                }

                // 文本改变监听
                rename.document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str
                        val existFile2 = isImageExist(it.key == "Drawable", str)
                        it2.existFile = existFile2 != null
                        hint.isVisible = existFile2 != null
                        if (existFile2 != null) {
                            existFileImageView.isVisible = true
                            existFileImageView.setImage(File(existFile2.path))
                        } else {
                            existFileImageView.isVisible = false
                        }
                    }

                    override fun removeUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str
                        val existFile2 = isImageExist(it.key == "Drawable", str)
                        it2.existFile = existFile2 != null
                        hint.isVisible = existFile2 != null
                        if (existFile2 != null) {
                            existFileImageView.isVisible = true
                            existFileImageView.setImage(File(existFile2.path))
                        } else {
                            existFileImageView.isVisible = false
                        }
                    }

                    override fun changedUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str
                        val existFile2 = isImageExist(it.key == "Drawable", str)
                        it2.existFile = existFile2 != null
                        hint.isVisible = existFile2 != null
                        if (existFile2 != null) {
                            existFileImageView.isVisible = true
                            existFileImageView.setImage(File(existFile2.path))
                        } else {
                            existFileImageView.isVisible = false
                        }
                    }
                })
            }
        }

        rootPane.addComponentListener(object : SimpleComponentListener() {
            override fun componentResized(p0: ComponentEvent?) {
                val width = rootPane.width
                for (component in rootPane.components) {
                    if (component is JPanel) {
                        for (component2 in component.components) {
                            if (component2 is JLabel) {
                                component2.preferredSize = Dimension((width * 0.4).toInt(), JBUI.scale(35))
                            } else if (component2 is Box) {
                                val child = component2.getComponent(0)
                                if (child is JTextField) {
                                    child.preferredSize = Dimension(width - (width * 0.4).toInt() - 10, JBUI.scale(35))
                                }
                            }
                        }
                    }
                }
                rootPane.updateUI()
            }
        })

        rootPane.preferredSize = JBUI.size(600, totalHeight)

        val scrollPane = ScrollPaneFactory.createScrollPane(rootPane)
        scrollPane.border = LineBorder(Config.getLineColor(), 0, 0, 1, 0)
        scrollPane.preferredSize = JBUI.size(600, 300)
        return scrollPane
    }

    private fun getFile(files: List<VirtualFile>?, isDrawable: Boolean, name: String): File? {
        if (files.isNullOrEmpty()) {
            return null
        }

        for (file in files) {
            if (isDrawable && file.path.contains("drawable")) {
                if (file.name == name) {
                    return File(file.path)
                }
            } else if (!isDrawable && file.path.contains("mipmap")) {
                if (file.name == name) {
                    return File(file.path)
                }
            }
        }

        return null
    }

    /**
     * 构建底部按钮面板
     */
    private fun createAction(): JComponent {
        val bottomPane = Box.createHorizontalBox()
        bottomPane.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        mJChosenFolder = JLabel()
        mJChosenFolder.border = BorderFactory.createEmptyBorder(0, 0, 0, 10)
        bottomPane.add(mJChosenFolder)
        val chooseFolderBtn = JButton("change folder")
        bottomPane.add(chooseFolderBtn)
        bottomPane.add(Box.createHorizontalGlue())
        val cancelBtn = JButton("cancel")
        bottomPane.add(cancelBtn)
        mBtnOk = JButton("import")
        bottomPane.add(mBtnOk)

        chooseFolderBtn.addActionListener {
            if (mCardShow == CARD_RENAME) {
                chooseFolderBtn.isVisible = false
                cancelBtn.isVisible = false
                mBtnOk.text = "ok"
                mCardShow = CARD_FILE
                val layout = mCardPane.layout as CardLayout
                layout.show(mCardPane, CARD_FILE)
                myTree.requestFocus()
            }
        }

        cancelBtn.addActionListener {
            isVisible = false
        }

        mBtnOk.addActionListener {
            if (mCardShow == CARD_FILE) {
                chooseFolderBtn.isVisible = true
                cancelBtn.isVisible = true
                mBtnOk.text = "import"
                mCardShow = CARD_RENAME
                val layout = mCardPane.layout as CardLayout
                layout.show(mCardPane, CARD_RENAME)
            } else {
                doOKAction()
            }
        }

        return bottomPane
    }

    private fun doOKAction() {
        if (mSelectedFolder == null || !mSelectedFolder!!.isDirectory) {
            Toast.show(rootPane, MessageType.ERROR, "请选择文件夹")
            return
        }
        isVisible = false
        mOkActionListener?.invoke()
    }

    /**
     * 获取选中的文件夹
     */
    fun getSelectedFolder(): VirtualFile? {
        return mSelectedFolder
    }

    /**
     * 选择文件夹
     */
    private fun selectFolder(file: VirtualFile) {
        // Select element in the tree
        ApplicationManager.getApplication().invokeLater({
            @Suppress("UnstableApiUsage")
            mBuilder?.selectAsync(file, file, false)
        }, ModalityState.stateForComponent(rootPane))
    }

    /**
     * 获取重命名文件Map，key为原始文件名称，value为重命名的值
     */
    fun getRenameFileMap(): Map<String, List<RenameEntity>> {
        return mRenameFileMap
    }

    /**
     * 设置确定按钮点击监听
     */
    fun setOkActionListener(listener: (() -> Unit)?) {
        mOkActionListener = listener
    }

    private fun handleSelectionChanged() {
        mBtnOk.isEnabled = isChosenFolder()
        if (mSelectedFolder == null) {
            mJChosenFolder.text = null
        } else {
            mJChosenFolder.text = mSelectedFolder?.path?.replace(project.basePath ?: "", "")
        }
    }

    private fun isChosenFolder(): Boolean {
        val path = myTree.selectionPath ?: return false
        val node = path.lastPathComponent as DefaultMutableTreeNode
        val userObject = node.userObject as? ProjectViewNode<*> ?: return false
        val vFile = userObject.virtualFile
        return (vFile != null).apply {
            mSelectedFolder = vFile

        }
    }

    private fun filterFiles(list: Array<*>): Array<Any> {
        val condition = Condition { psiFile: PsiFile ->
            if (!psiFile.isDirectory) {
                return@Condition false
            } else if (filter != null && !filter.accept(psiFile)) {
                return@Condition false
            }

            true
        }

        val result: MutableList<Any?> = ArrayList(list.size)
        for (obj in list) {
            val psiFile: PsiFile? = when (obj) {
                is PsiFile -> {
                    obj
                }

                is PsiFileNode -> {
                    obj.value
                }

                else -> {
                    null
                }
            }
            if (psiFile != null && !condition.value(psiFile)) {
                continue
            } else if (obj is ProjectViewNode<*>) {
                if (!obj.canHaveChildrenMatching(condition)) {
                    continue
                }
            }
            result.add(obj)
        }
        return ArrayUtil.toObjectArray(result)
    }

    /**
     * 判断指定图片是否已存在,存在则返回同名文件
     */
    private fun isImageExist(isDrawable: Boolean, fileName: String): VirtualFile? {
        val rootDir = mSelectedFolder?.path ?: return null

        var selectFile: VirtualFile?
        if (isDrawable) {
            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/drawable-xhdpi/$fileName")
            if (selectFile != null) {
                return selectFile
            }

            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/drawable-xxhdpi/$fileName")
            if (selectFile != null) {
                return selectFile
            }

            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/drawable/$fileName")
            if (selectFile != null) {
                return selectFile
            }

            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/drawable-hdpi/$fileName")
            if (selectFile != null) {
                return selectFile
            }

            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/drawable-mdpi/$fileName")
            if (selectFile != null) {
                return selectFile
            }

            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/drawable-xxxhdpi/$fileName")
            if (selectFile != null) {
                return selectFile
            }

            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/drawable-nodpi/$fileName")
            if (selectFile != null) {
                return selectFile
            }
        } else {
            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/mipmap-xhdpi/$fileName")
            if (selectFile != null) {
                return selectFile
            }

            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/mipmap-xxhdpi/$fileName")
            if (selectFile != null) {
                return selectFile
            }

            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/mipmap-hdpi/$fileName")
            if (selectFile != null) {
                return selectFile
            }

            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/mipmap-mdpi/$fileName")
            if (selectFile != null) {
                return selectFile
            }

            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/mipmap-xxxhdpi/$fileName")
            if (selectFile != null) {
                return selectFile
            }

            selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/mipmap/$fileName")
            if (selectFile != null) {
                return selectFile
            }
        }

        return null
    }

    companion object {
        /**
         * 文件选择面板
         */
        private const val CARD_FILE = "file"

        /**
         * 文件重命名面板
         */
        private const val CARD_RENAME = "rename"
    }
}

/**
 * 重命名实体
 */
data class RenameEntity(
    /**
     * 导入的原文件名称
     */
    var oldName: String,
    /**
     * 重命名的名称
     */
    var newName: String,
    /**
     * 存在同名文件
     */
    var existFile: Boolean = false,
    /**
     * 如果存在同名文件，是否覆盖同名文件
     */
    var coverExistFile: Boolean = false
)
