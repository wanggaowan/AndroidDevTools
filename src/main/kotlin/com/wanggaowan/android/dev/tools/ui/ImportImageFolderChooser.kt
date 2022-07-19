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
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.JBUI
import com.wanggaowan.android.dev.tools.listener.SimpleComponentListener
import java.awt.*
import java.awt.event.ComponentEvent
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
    private val initialFile: VirtualFile? = null,
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
    private var mCardShow = CARD_FILE
    private var mRenameFileMap = mutableMapOf<String, MutableMap<String, String>>()

    /**
     * 确定按钮点击监听
     */
    private var mOkActionListener: (() -> Unit)? = null

    init {
        val isDarkTheme = ColorUtil.isDark(background)
        Config.isDarkTheme = isDarkTheme

        isAlwaysOnTop = true
        setTitle(title)

        val rootPanel = JPanel(BorderLayout())
        contentPane = rootPanel
        val layout = CardLayout()
        mCardPane = JPanel(layout)
        mCardPane.add(createFileChoosePanel(), CARD_FILE)
        mCardPane.add(createRenameFilePanel(renameFiles), CARD_RENAME)
        rootPanel.add(mCardPane, BorderLayout.CENTER)
        rootPanel.add(createAction(), BorderLayout.SOUTH)
        pack()

        if (initialFile != null) {
            // dialog does not exist yet
            SwingUtilities.invokeLater { selectFolder(initialFile) }
        }

        SwingUtilities.invokeLater { handleSelectionChanged() }
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
        scrollPane.preferredSize = JBUI.size(500, 300)
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

            var map = mRenameFileMap[parentName]
            if (map == null) {
                map = mutableMapOf()
                mRenameFileMap[parentName] = map
            }
            map[it.name] = it.name
        }

        var totalHeight = 0
        mRenameFileMap.forEach {
            val type = JLabel(it.key + "：")
            type.preferredSize = JBUI.size(500, 40)
            type.border = BorderFactory.createEmptyBorder(0, 5, 5, 5)
            val font = type.font
            val fontSize = if (font == null) 16 else font.size + 2
            type.font = Font(null, Font.BOLD, fontSize)
            rootPane.add(type)
            totalHeight += 40

            it.value.forEach { it2 ->
                val panel = JPanel(BorderLayout())
                panel.border = BorderFactory.createEmptyBorder(0, 5, 5, 5)
                totalHeight += 40

                val title = JLabel(it2.key + "：")
                title.preferredSize = JBUI.size(200, 35)
                panel.add(title, BorderLayout.WEST)

                val rename = JTextField(it2.value)
                rename.preferredSize = JBUI.size(290, 35)
                panel.add(rename, BorderLayout.CENTER)

                rootPane.add(panel)

                // 文本改变监听
                rename.document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        mRenameFileMap[it.key]?.let { map ->
                            map[it2.key] = str
                        }
                    }

                    override fun removeUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        mRenameFileMap[it.key]?.let { map ->
                            map[it2.key] = str
                        }
                    }

                    override fun changedUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        mRenameFileMap[it.key]?.let { map ->
                            map[it2.key] = str
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
                            } else if (component2 is JTextField) {
                                component2.preferredSize = Dimension(width - (width * 0.4).toInt() - 10, JBUI.scale(35))
                            }
                        }
                    }
                }
                rootPane.updateUI()
            }
        })

        rootPane.preferredSize = JBUI.size(500, totalHeight)

        val scrollPane = ScrollPaneFactory.createScrollPane(rootPane)
        scrollPane.border = LineBorder(Config.getLineColor(), 0, 0, 1, 0)
        scrollPane.preferredSize = JBUI.size(500, 300)
        return scrollPane
    }

    /**
     * 构建底部按钮面板
     */
    private fun createAction(): JComponent {
        val bottomPane = Box.createHorizontalBox()
        bottomPane.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        val renameBtn = JButton("重命名导入的文件")
        bottomPane.add(renameBtn)
        bottomPane.add(Box.createHorizontalGlue())
        val cancelBtn = JButton("cancel")
        bottomPane.add(cancelBtn)
        mBtnOk = JButton("ok")
        bottomPane.add(mBtnOk)

        renameBtn.addActionListener {
            if (mCardShow == CARD_FILE) {
                renameBtn.isVisible = false
                cancelBtn.isVisible = false
                mCardShow = CARD_RENAME
                val layout = mCardPane.layout as CardLayout
                layout.show(mCardPane, CARD_RENAME)
            }
        }

        cancelBtn.addActionListener {
            isVisible = false
        }

        mBtnOk.addActionListener {
            if (mCardShow == CARD_RENAME) {
                renameBtn.isVisible = true
                cancelBtn.isVisible = true
                mCardShow = CARD_FILE
                val layout = mCardPane.layout as CardLayout
                layout.show(mCardPane, CARD_FILE)
                myTree.requestFocus()
            } else {
                doOKAction()
            }
        }

        return bottomPane
    }

    private fun doOKAction() {
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
            mBuilder?.selectAsync(file, file, true)
        }, ModalityState.stateForComponent(rootPane))
    }

    /**
     * 获取重命名文件Map，key为原始文件名称，value为重命名的值
     */
    fun getRenameFileMap(): Map<String, Map<String, String>> {
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
