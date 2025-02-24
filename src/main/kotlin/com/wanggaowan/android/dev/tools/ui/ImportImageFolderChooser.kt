package com.wanggaowan.android.dev.tools.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.getTreePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.wanggaowan.android.dev.tools.utils.Toast
import com.wanggaowan.android.dev.tools.utils.ex.rootDir
import java.awt.*
import java.io.File
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.*

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
) : JDialog() {
    private lateinit var myTree: JTree
    private lateinit var mBtnOk: JButton
    private lateinit var mJChosenFolder: JLabel
    private lateinit var mJRenamePanel: JPanel

    /**
     * 切换文件选择/重命名面板的父面板
     */
    private var mCardPane: JPanel

    /**
     * 选中的文件夹
     */
    private var mSelectedFolder: VirtualFile? = null

    private var mCardShow = CARD_RENAME
    private var mRenameFileMap = mutableMapOf<String, MutableList<RenameEntity>>()

    /**
     * 确定按钮点击监听
     */
    private var mOkActionListener: (() -> Unit)? = null

    /**
     * 确定按钮点击监听
     */
    private var mCancelActionListener: (() -> Unit)? = null

    private var mDoOk = false

    init {
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
            mBtnOk.isEnabled = true
            mJChosenFolder.text = initialFile.path.replace(project.basePath ?: "", "")
        } else {
            mBtnOk.isEnabled = false
        }
    }

    override fun setVisible(visible: Boolean) {
        if (visible) {
            val window = WindowManager.getInstance().suggestParentWindow(project)
            window?.let {
                location =
                    Point(it.x + (it.width - this.width) / 2, it.y + (it.height - this.height) / 2)
            }
        }
        super.setVisible(visible)
        if (!visible && !mDoOk) {
            mCancelActionListener?.invoke()
        }
    }

    /**
     * 构建文件选择面板
     */
    private fun createFileChoosePanel(): JComponent {
        val model = DefaultTreeModel(FileTreeNode(project.rootDir))
        myTree = com.intellij.ui.treeStructure.Tree(model)
        myTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        val render = DefaultTreeCellRenderer()
        render.leafIcon = render.defaultClosedIcon
        render.backgroundNonSelectionColor = JBColor("", Color(0x00000000, true))
        render.backgroundSelectionColor = JBColor("", Color(0x00000000, true))
        render.borderSelectionColor = null
        myTree.cellRenderer = render

        if (mSelectedFolder != null) {
            val path = model.getTreePath(mSelectedFolder)
            myTree.selectionModel.selectionPath = path
        } else {
            myTree.expandRow(0)
        }

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

            list.add(RenameEntity(it.name, it, it.name))
        }

        mJRenamePanel = JPanel(GridBagLayout())
        initRenamePanel()
        val scrollPane = ScrollPaneFactory.createScrollPane(mJRenamePanel)
        scrollPane.preferredSize = JBUI.size(600, 300)
        scrollPane.border = LineBorder(UIColor.LINE_COLOR, 0, 0, 1, 0)
        return scrollPane
    }

    private fun initRenamePanel() {
        mJRenamePanel.removeAll()
        var depth = 0
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.HORIZONTAL
        c.weightx = 1.0

        mRenameFileMap.forEach {
            val type = JLabel(it.key + "：")
            type.border = BorderFactory.createEmptyBorder(if (depth > 0) 10 else 0, 5, 5, 5)
            val fontSize = (UIUtil.getFontSize(UIUtil.FontSize.NORMAL) + 2).toInt()
            type.font = Font(type.font.name, Font.BOLD, fontSize)
            c.gridy = depth++
            mJRenamePanel.add(type, c)

            val cc = GridBagConstraints()
            cc.fill = GridBagConstraints.HORIZONTAL
            cc.weightx = 1.0

            it.value.forEach { it2 ->
                val panel = JPanel(GridBagLayout())
                panel.border = BorderFactory.createEmptyBorder(0, 5, 5, 5)
                c.gridy = depth++
                mJRenamePanel.add(panel, c)

                val box = Box.createHorizontalBox()
                cc.gridy = 0
                panel.add(box, cc)

                val imageView = ImageView(File(it2.oldFile.path))
                imageView.preferredSize = JBUI.size(34)
                imageView.maximumSize = JBUI.size(34)
                imageView.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
                box.add(imageView)

                val rename = ExtensionTextField(it2.newName, placeHolder = it2.oldName)
                rename.minimumSize = JBUI.size(400, 34)
                box.add(rename)

                val box2 = Box.createHorizontalBox()
                cc.gridy = 1
                box2.border = BorderFactory.createEmptyBorder(2, 0, 2, 0)
                panel.add(box2, cc)

                val (existFile, isInMap) = isImageExist(it.key, it2)
                val existFileImageView =
                    ImageView(if (existFile != null) File(existFile.path) else null)
                existFileImageView.preferredSize = JBUI.size(34, 16)
                existFileImageView.minimumSize = JBUI.size(34, 16)
                existFileImageView.maximumSize = JBUI.size(34, 16)
                existFileImageView.border = BorderFactory.createEmptyBorder(0, 9, 0, 9)
                existFileImageView.isVisible = existFile != null
                box2.add(existFileImageView)

                val hintStr =
                    if (isInMap) "导入的文件存在相同文件，勾选则导入最后一个同名文件，否则导入第一个同名文件" else "已存在同名文件,是否覆盖原文件？不勾选则跳过导入"
                val hint = JCheckBox(hintStr)
                hint.foreground = JBColor.RED
                hint.font = UIUtil.getFont(UIUtil.FontSize.MINI, rename.font)
                it2.existFile = existFile != null
                hint.isVisible = existFile != null
                hint.minimumSize = JBUI.size(400, 22)
                box2.add(hint)
                box2.add(Box.createHorizontalGlue())

                hint.addChangeListener {
                    it2.coverExistFile = hint.isSelected
                }

                // 文本改变监听
                rename.document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str.ifEmpty { it2.oldName }
                        refreshRenamePanel()
                    }

                    override fun removeUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str.ifEmpty { it2.oldName }
                        refreshRenamePanel()
                    }

                    override fun changedUpdate(p0: DocumentEvent?) {
                        val str = rename.text.trim()
                        it2.newName = str.ifEmpty { it2.oldName }
                        refreshRenamePanel()
                    }
                })
            }
        }

        val placeHolder = JLabel()
        c.weighty = 1.0
        c.gridy = depth++
        mJRenamePanel.add(placeHolder, c)
    }

    private fun refreshRenamePanel() {
        var isDrawable = false
        var index = 0
        for (component in mJRenamePanel.components) {
            if (component is JLabel) { // 分类标题
                val value = component.text.trim() == "Drawable："
                if (value != isDrawable) {
                    index = 0
                }
                isDrawable = value
            } else if (component is JPanel) {
                val hintRoot = component.getComponent(1) as Box?
                val imageView = hintRoot?.getComponent(0) as? ImageView
                val checkBox = hintRoot?.getComponent(1) as? JCheckBox
                val key = if (isDrawable) "Drawable" else "Mipmap"
                val values = mRenameFileMap[key]
                if (values != null && index < values.size) {
                    values[index].also { entity ->
                        refreshHintVisible(key, entity, checkBox, imageView)
                    }
                }
                index++
            }
        }
    }

    private fun refreshHintVisible(
        groupKey: String, entity: RenameEntity, hint: JCheckBox?, imageView: ImageView?
    ) {
        val (existFile2, isInMap) = isImageExist(groupKey, entity)
        entity.existFile = existFile2 != null
        val hintStr =
            if (isInMap) "导入的文件存在相同文件，勾选则导入最后一个同名文件，否则导入第一个同名文件" else "已存在同名文件,是否覆盖原文件？不勾选则跳过导入"
        hint?.text = hintStr
        val preVisible = hint?.isVisible
        val visible = existFile2 != null
        if (preVisible != visible) {
            hint?.isVisible = visible
            if (existFile2 != null) {
                imageView?.isVisible = true
                imageView?.setImage(File(existFile2.path))
            } else {
                imageView?.isVisible = false
            }
        }
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
        mDoOk = true
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

    /**
     * 设置取消按钮点击监听
     */
    fun setCancelActionListener(listener: (() -> Unit)?) {
        mCancelActionListener = listener
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
        val node = path.lastPathComponent
        if (node !is FileTreeNode) {
            return false
        }

        val vFile = node.file
        return (vFile != null).apply {
            mSelectedFolder = vFile
        }
    }

    /**
     * 判断指定图片是否已存在,存在则返回同名文件
     */
    private fun isImageExist(groupKey: String, entity: RenameEntity): Pair<VirtualFile?, Boolean> {
        val rootDir = mSelectedFolder?.path ?: return Pair(null, false)

        var selectFile: VirtualFile?
        val fileName = entity.newName
        if (groupKey == "Drawable") {
            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/drawable-xhdpi/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }

            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/drawable-xxhdpi/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }

            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/drawable/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }

            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/drawable-hdpi/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }

            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/drawable-mdpi/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }

            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/drawable-xxxhdpi/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }

            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/drawable-nodpi/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }
        } else {
            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/mipmap-xhdpi/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }

            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/mipmap-xxhdpi/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }

            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/mipmap-hdpi/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }

            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/mipmap-mdpi/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }

            selectFile = VirtualFileManager.getInstance()
                .findFileByUrl("file://${rootDir}/mipmap-xxxhdpi/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }

            selectFile =
                VirtualFileManager.getInstance().findFileByUrl("file://${rootDir}/mipmap/$fileName")
            if (selectFile != null) {
                return Pair(selectFile, false)
            }
        }

        val group = mRenameFileMap[groupKey]
        if (group != null) {
            for (e in group) {
                if (e != entity && entity.newName == e.newName) {
                    return Pair(e.oldFile, true)
                }
            }
        }


        return Pair(null, false)
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
     * 导入的原文件名称
     */
    var oldFile: VirtualFile,
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

class FileTreeNode(val file: VirtualFile?) : DefaultMutableTreeNode(file, true) {

    override fun getChildCount(): Int {
        return if (file == null) 0 else if (children != null) {
            children.size
        } else {
            file.children.forEach {
                if (it.isDirectory && !it.name.startsWith(".")) {
                    val index = if (children == null) 0 else children.size
                    insert(FileTreeNode(it), index)
                }
            }

            if (children == null) {
                children = Vector()
                0
            } else {
                children.size
            }
        }
    }

    override fun children(): Enumeration<TreeNode> {
        return if (file == null) EMPTY_ENUMERATION else if (children != null) {
            children.elements()
        } else {
            file.children.forEach {
                if (it.isDirectory && !it.name.startsWith(".")) {
                    val index = if (children == null) 0 else children.size
                    insert(FileTreeNode(it), index)
                }
            }

            if (children == null) {
                children = Vector()
                EMPTY_ENUMERATION
            } else {
                children.elements()
            }
        }
    }

    override fun isLeaf(): Boolean {
        val isLeft = super.isLeaf()
        return isLeft
    }

    override fun toString(): String {
        return file?.name ?: ""
    }
}
