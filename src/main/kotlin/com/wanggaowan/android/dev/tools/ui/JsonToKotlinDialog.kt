package com.wanggaowan.android.dev.tools.ui

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBUI
import com.wanggaowan.android.dev.tools.ui.language.JsonLanguageTextField
import com.wanggaowan.android.dev.tools.utils.ProgressUtils
import com.wanggaowan.android.dev.tools.utils.PropertiesSerializeUtils
import com.wanggaowan.android.dev.tools.utils.StringUtils
import com.wanggaowan.android.dev.tools.utils.Toast
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.*
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * json文件转Kotlin对象
 */
class JsonToKotlinDialog(
    private val project: Project,
    private val psiFile: PsiFile,
    private val selectElement: PsiElement?
) : DialogWrapper(project, false) {

    private val mCreateObjectName: ExtensionTextField =
        ExtensionTextField("", placeHolder = "请输入类名称")
    private val mObjSuffix: ExtensionTextField = ExtensionTextField("", placeHolder = "类名后缀")
    private val mEtJsonContent: EditorTextField = JsonLanguageTextField(project)
    private val mJPCardRoot: JPanel = JPanel(CardLayout())
    private val mActionConfig = ConfigAction {
        mCardShow = CARD_CONFIG
        it.isEnabled = false
        cancelAction.isEnabled = false
        (mJPCardRoot.layout as CardLayout).show(mJPCardRoot, mCardShow)
    }

    // 插入位置根节点
    private var mRootElement: KtClass? = null

    private var mCardShow = CARD_INPUT

    init {
        val headRootPanel = JPanel(GridBagLayout())

        val cc = GridBagConstraints()
        cc.fill = GridBagConstraints.HORIZONTAL
        cc.weightx = 0.0
        cc.gridx = 0

        val label = JLabel("类名称")
        label.border = BorderFactory.createEmptyBorder(0, 0, 0, JBUI.scale(6))
        headRootPanel.add(label, cc)

        cc.weightx = 10.0
        cc.gridx = 1
        mCreateObjectName.minimumSize = JBUI.size(100, 30)
        headRootPanel.add(mCreateObjectName, cc)

        cc.weightx = 0.0
        cc.gridx = 2
        val emptyLabel = JLabel()
        emptyLabel.border = BorderFactory.createEmptyBorder(0, 2, 0, JBUI.scale(2))
        headRootPanel.add(emptyLabel, cc)

        cc.weightx = 1.0
        cc.gridx = 3
        mObjSuffix.text = "Entity"
        mObjSuffix.minimumSize = JBUI.size(100, 30)
        headRootPanel.add(mObjSuffix, cc)

        val rootPanel = JPanel(BorderLayout())
        rootPanel.add(headRootPanel, BorderLayout.NORTH)

        val contentRootPanel = JPanel(BorderLayout())
        contentRootPanel.add(JLabel("JSON"), BorderLayout.NORTH)
        mEtJsonContent.isEnabled = true
        contentRootPanel.add(mEtJsonContent, BorderLayout.CENTER)
        rootPanel.add(contentRootPanel, BorderLayout.CENTER)
        mJPCardRoot.add(rootPanel, CARD_INPUT)
        mJPCardRoot.preferredSize = JBUI.size(1000, 500)

        initSetRoot()
        initData()

        init()
    }

    override fun createCenterPanel(): JComponent {
        return mJPCardRoot
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return if (mRootElement == null) mCreateObjectName else mEtJsonContent
    }

    override fun createLeftSideActions(): Array<Action> {
        return arrayOf(mActionConfig)
    }

    private fun initSetRoot() {
        val box = Box.createVerticalBox()

        val cbApplyAll = JCheckBox("以下配置是否应用于所有项目")
        cbApplyAll.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        cbApplyAll.isSelected = KTGsonFormatConfig.isApplyAllProject()
        box.add(cbApplyAll)

        val cbValueToDoc = JCheckBox("是否用Json值生成注释文档")
        cbValueToDoc.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        cbValueToDoc.isSelected = KTGsonFormatConfig.isJsonValueToDoc(project)
        cbValueToDoc.addChangeListener {
            KTGsonFormatConfig.setJsonValueToDoc(project, cbValueToDoc.isSelected)
        }
        box.add(cbValueToDoc)

        val cbCreateDataClass = JCheckBox("是否以Data class格式生成实体")
        cbCreateDataClass.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        cbCreateDataClass.isSelected = KTGsonFormatConfig.isCreateDataClass(project)
        cbCreateDataClass.addChangeListener {
            KTGsonFormatConfig.setCreateDataClass(project, cbCreateDataClass.isSelected)
        }
        box.add(cbCreateDataClass)

        val cbCreateNestClass = JCheckBox("如果JSON有多层，是否以嵌套类创建实体")
        cbCreateNestClass.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        cbCreateNestClass.isSelected = KTGsonFormatConfig.isCreateNestClass(project)
        cbCreateNestClass.addChangeListener {
            KTGsonFormatConfig.setCreateNestClass(project, cbCreateNestClass.isSelected)
        }
        box.add(cbCreateNestClass)

        cbApplyAll.addChangeListener {
            KTGsonFormatConfig.setApplyAllProject(cbApplyAll.isSelected)
            KTGsonFormatConfig.setJsonValueToDoc(project, cbValueToDoc.isSelected)
            KTGsonFormatConfig.setCreateDataClass(project, cbCreateDataClass.isSelected)
            KTGsonFormatConfig.setCreateNestClass(project, cbCreateNestClass.isSelected)
        }

        mJPCardRoot.add(box, CARD_CONFIG)
    }

    private fun initData() {
        if (selectElement == null) {
            mCreateObjectName.isEnabled = true
            return
        }

        if (selectElement is KtClass) {
            mCreateObjectName.isEnabled = false
            mRootElement = selectElement
            mCreateObjectName.text = selectElement.name
            return
        }

        val element = PsiTreeUtil.getParentOfType(selectElement, KtClass::class.java)
        if (element != null) {
            mRootElement = element
            mCreateObjectName.isEnabled = false
            mCreateObjectName.text = element.name
            return
        }

        mCreateObjectName.isEditable = true
    }

    override fun doOKAction() {
        if (mCardShow == CARD_CONFIG) {
            mCardShow = CARD_INPUT
            mActionConfig.isEnabled = true
            cancelAction.isEnabled = true
            (mJPCardRoot.layout as CardLayout).show(mJPCardRoot, mCardShow)
            return
        }

        val objName = mCreateObjectName.text
        if (objName.isNullOrEmpty()) {
            Toast.show(mCreateObjectName, MessageType.ERROR, "请输入要创建的对象名称")
            return
        }

        val jsonStr = mEtJsonContent.text
        if (jsonStr.isEmpty()) {
            Toast.show(mEtJsonContent, MessageType.ERROR, "请输入JSON内容")
            return
        }

        val jsonObject: JsonObject?
        try {
            jsonObject = Gson().fromJson(jsonStr, JsonObject::class.java)
            super.doOKAction()
        } catch (_: Exception) {
            Toast.show(mEtJsonContent, MessageType.ERROR, "JSON数据格式不正确")
            return
        }

        ProgressUtils.runBackground(project, "GsonFormat") { progressIndicator ->
            progressIndicator.isIndeterminate = true
            WriteCommandAction.runWriteCommandAction(project) {
                val factory = KtPsiFactory(project)
                val docFactory = KDocElementFactory(project)
                if (mRootElement == null) {
                    val element = createClass(factory, docFactory, objName, null)
                    val lastChild = psiFile.lastChild
                    createJavaObjectOnJsonObject(factory, docFactory, jsonObject, element)
                    psiFile.addAfter(element, lastChild)
                } else {
                    createJavaObjectOnJsonObject(factory, docFactory, jsonObject, mRootElement!!)
                }
                reformatFile(project, psiFile)
            }
            progressIndicator.isIndeterminate = false
            progressIndicator.fraction = 1.0
        }
    }

    private fun createClass(
        factory: KtPsiFactory,
        docFactory: KDocElementFactory,
        name: String,
        doc: String?
    ): KtClass {
        var kdoc: KDoc? = null
        if (KTGsonFormatConfig.isJsonValueToDoc(project) && !doc.isNullOrEmpty()) {
            // 不能把doc加到element，虽然此doc文档是属于element的，因为加到element下，doc结束符号'*/'与属性之间不会换行
            // 即使在'*/'后加换行'*/\n'也无效
            kdoc = docFactory.createKDocFromText("/**\n* $doc\n*/")
        }

        val suffix = mObjSuffix.text.trim()
        val ktClass = if (KTGsonFormatConfig.isCreateDataClass(project)) {
            factory.createClass("data class ${name}${suffix}(\n)")
        } else {
            factory.createClass("class ${name}${suffix}{\n}")
        }
        kdoc?.let { ktClass.addBefore(it, ktClass.firstChild) }

        return ktClass
    }

    private fun createJavaObjectOnJsonObject(
        factory: KtPsiFactory,
        docFactory: KDocElementFactory,
        jsonObject: JsonObject,
        parentElement: KtClass
    ) {
        jsonObject.keySet().forEach {
            val obj = jsonObject.get(it)
            if (obj == null || obj.isJsonNull) {
                addField(factory, docFactory, it, null, parentElement)
            } else if (obj.isJsonPrimitive) {
                addField(factory, docFactory, it, obj as JsonPrimitive, parentElement)
            } else if (obj.isJsonObject) {
                var doc: String? = null
                val key: String
                if (it.contains("(") && it.contains(")")) {
                    // 兼容周卓接口文档JSON, "dataList (产线数据)":[]
                    val index = it.indexOf("(")
                    doc = it.substring(index + 1, it.length - 1)
                    key = it.substring(0, index).replace(" ", "")
                } else {
                    key = it
                }
                val className = StringUtils.toHumpFormat(key)

                val suffix = mObjSuffix.text.trim()
                addFieldForObjType(
                    factory,
                    docFactory,
                    key,
                    className + suffix,
                    false,
                    parentElement,
                    doc
                )

                val element = createClass(factory, docFactory, className, doc)
                createJavaObjectOnJsonObject(factory, docFactory, obj as JsonObject, element)
                if (KTGsonFormatConfig.isCreateNestClass(project)) {
                    val body = parentElement.getOrCreateBody()
                    body.addBefore(element, body.lastChild)
                } else {
                    psiFile.add(element)
                }
            } else if (obj.isJsonArray) {
                var doc: String? = null
                val key: String
                if (it.contains("(") && it.contains(")")) {
                    // 兼容周卓接口文档JSON, "dataList (产线数据)":[]
                    val index = it.indexOf("(")
                    doc = it.substring(index + 1, it.length - 1)
                    key = it.substring(0, index).replace(" ", "")
                } else {
                    key = it
                }
                val className = StringUtils.toHumpFormat(key)

                obj.asJsonArray.let { jsonArray ->
                    if (jsonArray.size() == 0) {
                        addFieldForObjType(
                            factory,
                            docFactory,
                            key,
                            "any",
                            true,
                            parentElement,
                            doc
                        )
                        return@let
                    }

                    var typeSame = true
                    var type: String? = null
                    var isCreateObjChild = false
                    jsonArray.forEach { child ->
                        if (child.isJsonObject) {
                            if (type == null) {
                                type = "JsonObject"
                            } else {
                                typeSame = "JsonObject" == type
                            }

                            if (!isCreateObjChild) {
                                isCreateObjChild = true
                                val element = createClass(factory, docFactory, className, doc)
                                createJavaObjectOnJsonObject(
                                    factory,
                                    docFactory,
                                    child as JsonObject,
                                    element
                                )
                                if (KTGsonFormatConfig.isCreateNestClass(project)) {
                                    val body = parentElement.getOrCreateBody()
                                    body.addBefore(element, body.lastChild)
                                } else {
                                    psiFile.add(element)
                                }
                            }
                        } else if (child.isJsonPrimitive) {
                            val typeName = child.asJsonPrimitive.let { primitive ->
                                if (primitive.isNumber) {
                                    "number"
                                } else if (primitive.isBoolean) {
                                    "boolean"
                                } else if (primitive.isString) {
                                    "string"
                                } else {
                                    "null"
                                }
                            }

                            if (type == null) {
                                type = typeName
                            } else {
                                typeSame = typeName == type
                            }
                        } else if (child.isJsonArray) {
                            typeSame = false
                        }
                    }

                    if (!typeSame) {
                        addFieldForObjType(
                            factory,
                            docFactory,
                            key,
                            "any",
                            true,
                            parentElement,
                            doc
                        )
                        return@let
                    }

                    when (type) {
                        null, "null" -> {
                            addFieldForObjType(
                                factory,
                                docFactory,
                                key,
                                "any",
                                true,
                                parentElement,
                                doc
                            )
                        }

                        "JsonObject" -> {
                            val suffix = mObjSuffix.text.trim()
                            addFieldForObjType(
                                factory,
                                docFactory,
                                key,
                                className + suffix,
                                true,
                                parentElement,
                                doc
                            )
                        }

                        else -> {
                            addFieldForObjType(
                                factory,
                                docFactory,
                                key,
                                type,
                                true,
                                parentElement,
                                doc
                            )
                        }
                    }
                }
            }
        }
    }

    private fun addField(
        factory: KtPsiFactory,
        docFactory: KDocElementFactory,
        key: String,
        jsonPrimitive: JsonPrimitive?,
        parentElement: KtClass
    ) {

        val type = if (jsonPrimitive == null) {
            "Any"
        } else if (jsonPrimitive.isBoolean) {
            "boolean"
        } else if (jsonPrimitive.isNumber) {
            if (jsonPrimitive.asString.contains(".")) "Double" else "Int"
        } else {
            "String"
        }
        val content = "var $key: $type? = null"

        var doc: KDoc? = null
        if (KTGsonFormatConfig.isJsonValueToDoc(project) && jsonPrimitive != null) {
            // 不能把doc加到element，虽然此doc文档是属于element的，因为加到element下，doc结束符号'*/'与属性之间不会换行
            // 即使在'*/'后加换行'*/\n'也无效
            doc = docFactory.createKDocFromText(
                "/**\n* ${jsonPrimitive.toString().replace("\"", "")}\n*/"
            )
        }

        if (KTGsonFormatConfig.isCreateDataClass(project)) {
            var list = parentElement.getPrimaryConstructorParameterList()
            if (list == null) {
                list = parentElement.createPrimaryConstructorParameterListIfAbsent()
            }

            if (list.trailingComma == null && list.children.isNotEmpty()) {
                // 尾部逗号
                list.addBefore(factory.createComma(), list.lastChild)
            }

            val element = factory.createParameter(content)
            doc?.let { element.addBefore(it, element.firstChild) }
            list.addBefore(factory.createWhiteSpace("\n"), list.lastChild)
            list.addBefore(element, list.lastChild)
        } else {
            val element = factory.createProperty(content)
            doc?.let { element.addBefore(it, element.firstChild) }

            val body = parentElement.getOrCreateBody()
            addFieldBeforeClass(body, element)
        }
    }

    private fun addFieldForObjType(
        factory: KtPsiFactory,
        docFactory: KDocElementFactory,
        key: String,
        typeName: String,
        isArray: Boolean,
        parentElement: KtClass,
        doc: String?
    ) {

        val content = if ("any" == typeName) {
            "var $key: Any? = null"
        } else {
            if (isArray) "var $key: List<$typeName>? = null" else "var $key: $typeName? = null"
        }

        var kDoc: KDoc? = null
        if (KTGsonFormatConfig.isJsonValueToDoc(project) && !doc.isNullOrEmpty()) {
            // 不能把doc加到element，虽然此doc文档是属于element的，因为加到element下，doc结束符号'*/'与属性之间不会换行
            // 即使在'*/'后加换行'*/\n'也无效
            kDoc = docFactory.createKDocFromText("/**\n* $doc\n*/")
        }

        if (KTGsonFormatConfig.isCreateDataClass(project)) {
            var list = parentElement.getPrimaryConstructorParameterList()
            if (list == null) {
                list = parentElement.createPrimaryConstructorParameterListIfAbsent()
            }

            if (list.trailingComma == null && list.children.isNotEmpty()) {
                list.addBefore(factory.createComma(), list.lastChild)
            }

            val element = factory.createParameter(content)
            kDoc?.let { element.addBefore(it, element.firstChild) }

            list.addBefore(factory.createWhiteSpace("\n"), list.lastChild)
            list.addBefore(element, list.lastChild)
        } else {
            val element = factory.createProperty(content)
            kDoc?.let { element.addBefore(it, element.firstChild) }

            val body = parentElement.getOrCreateBody()
            addFieldBeforeClass(body, element)
        }
    }

    /**
     * 添加字段到父对象的class节点之前
     */
    private fun addFieldBeforeClass(parent: KtClassBody, addChild: PsiElement) {
        var anchor: PsiElement? = null
        for (child in parent.children) {
            if (child is KtClass || child is KtNamedFunction) {
                anchor = child
                break
            }
        }

        if (anchor == null) {
            anchor = parent.lastChild
        }
        parent.addBefore(addChild, anchor)
    }

    /**
     * 执行格式化
     *
     * @param project     项目对象
     * @param psiFile 需要格式化文件
     */
    private fun reformatFile(project: Project, psiFile: PsiFile) {
        CodeStyleManager.getInstance(project).reformatText(
            psiFile,
            mutableListOf(TextRange(0, psiFile.textLength))
        )
    }

    companion object {
        private const val CARD_INPUT = "input"
        private const val CARD_CONFIG = "config"
    }
}

class ConfigAction(private val myHelpActionPerformed: ((ConfigAction) -> Unit)?) :
    AbstractAction("Config") {
    override fun actionPerformed(e: ActionEvent) {
        myHelpActionPerformed?.invoke(this)
    }
}

internal object KTGsonFormatConfig {
    /**
     * json值是否作为文档生成
     */
    private const val APPLY_ALL_PROJECT = "kotlin_gson_format_config_apply_all_project"

    /**
     * json值是否作为文档生成
     */
    private const val JSON_VALUE_TO_DOC = "kotlin_gson_format_config_json_value_to_doc"

    /**
     * 是否要创建data class
     */
    private const val CREATE_DATA_CLASS = "kotlin_gson_format_config_create_data_class"

    /**
     * 是否创建嵌套类
     */
    private const val CREATE_NESTED_CLASS = "kotlin_gson_format_config_create_nested_class"

    fun isApplyAllProject(): Boolean {
        return PropertiesSerializeUtils.getBoolean(APPLY_ALL_PROJECT)
    }

    fun setApplyAllProject(isApplyAll: Boolean) {
        PropertiesSerializeUtils.putBoolean(APPLY_ALL_PROJECT, isApplyAll)
    }

    fun isJsonValueToDoc(project: Project): Boolean {
        return if (isApplyAllProject()) {
            PropertiesSerializeUtils.getBoolean(JSON_VALUE_TO_DOC, true)
        } else {
            PropertiesSerializeUtils.getBoolean(project, JSON_VALUE_TO_DOC, true)
        }
    }

    fun setJsonValueToDoc(project: Project, value: Boolean) {
        if (isApplyAllProject()) {
            PropertiesSerializeUtils.putBoolean(JSON_VALUE_TO_DOC, value)
        } else {
            PropertiesSerializeUtils.putBoolean(project, JSON_VALUE_TO_DOC, value)
        }
    }

    fun isCreateDataClass(project: Project): Boolean {
        return if (isApplyAllProject()) {
            PropertiesSerializeUtils.getBoolean(CREATE_DATA_CLASS)
        } else {
            PropertiesSerializeUtils.getBoolean(project, CREATE_DATA_CLASS)
        }
    }

    fun setCreateDataClass(project: Project, value: Boolean) {
        if (isApplyAllProject()) {
            PropertiesSerializeUtils.putBoolean(CREATE_DATA_CLASS, value)
        } else {
            PropertiesSerializeUtils.putBoolean(project, CREATE_DATA_CLASS, value)
        }
    }

    fun isCreateNestClass(project: Project): Boolean {
        return if (isApplyAllProject()) {
            PropertiesSerializeUtils.getBoolean(CREATE_NESTED_CLASS)
        } else {
            PropertiesSerializeUtils.getBoolean(project, CREATE_NESTED_CLASS)
        }
    }

    fun setCreateNestClass(project: Project, value: Boolean) {
        if (isApplyAllProject()) {
            PropertiesSerializeUtils.putBoolean(CREATE_NESTED_CLASS, value)
        } else {
            PropertiesSerializeUtils.putBoolean(project, CREATE_NESTED_CLASS, value)
        }
    }
}


