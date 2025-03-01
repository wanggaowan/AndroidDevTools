package com.wanggaowan.android.dev.tools.ui

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBUI
import com.wanggaowan.android.dev.tools.ui.language.JsonLanguageTextField
import com.wanggaowan.android.dev.tools.utils.ProgressUtils
import com.wanggaowan.android.dev.tools.utils.StringUtils
import com.wanggaowan.android.dev.tools.utils.Toast
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * json文件转JAVA对象
 */
class JsonToJavaDialog(
    private val project: Project,
    private val psiFile: PsiFile,
    private val selectElement: PsiElement?
) : DialogWrapper(project, false) {

    private val mRootPanel: JPanel = JPanel(BorderLayout())
    private val mCreateObjectName: ExtensionTextField =
        ExtensionTextField("", placeHolder = "请输入类名称")
    private val mObjSuffix: ExtensionTextField = ExtensionTextField("", placeHolder = "类名后缀")
    private val mEtJsonContent: EditorTextField = JsonLanguageTextField(project)

    // 插入位置根节点
    private var mRootElement: PsiElement? = null
    private var mCreateDoc = false

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
        mRootPanel.add(headRootPanel, BorderLayout.NORTH)

        val contentRootPanel = JPanel(BorderLayout())
        contentRootPanel.add(JLabel("JSON"), BorderLayout.NORTH)
        mEtJsonContent.isEnabled = true
        contentRootPanel.add(mEtJsonContent, BorderLayout.CENTER)
        mRootPanel.add(contentRootPanel, BorderLayout.CENTER)

        mRootPanel.preferredSize = JBUI.size(1000, 500)

        val doNotAskOption: com.intellij.openapi.ui.DoNotAskOption =
            object : com.intellij.openapi.ui.DoNotAskOption.Adapter() {
                override fun getDoNotShowMessage(): String {
                    return "json value 生成注释文档"
                }

                override fun shouldSaveOptionsOnCancel(): Boolean {
                    return true
                }

                override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                    if (exitCode == 0 && isSelected) {
                        mCreateDoc = true
                    }
                }
            }
        setDoNotAskOption(doNotAskOption)
        initData()

        init()
    }

    override fun createCenterPanel(): JComponent {
        return mRootPanel
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return if (mRootElement == null) mCreateObjectName else mEtJsonContent
    }

    private fun initData() {
        if (selectElement == null) {
            mCreateObjectName.isEnabled = true
            return
        }

        if (selectElement is PsiClass) {
            mCreateObjectName.isEnabled = false
            mRootElement = selectElement
            mCreateObjectName.text = selectElement.name
            return
        }

        val element = PsiTreeUtil.getParentOfType(selectElement, PsiClass::class.java)
        if (element != null) {
            mRootElement = element
            mCreateObjectName.isEnabled = false
            mCreateObjectName.text = element.name
            return
        }

        mCreateObjectName.isEditable = true
    }

    override fun doOKAction() {
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
                val factory = JavaPsiFacade.getElementFactory(project)
                if (mRootElement == null) {
                    val element = createClass(factory, objName)
                    val lastChild = psiFile.lastChild
                    createJavaObjectOnJsonObject(factory, jsonObject, element)
                    psiFile.addAfter(element, lastChild)
                } else {
                    createJavaObjectOnJsonObject(factory, jsonObject, mRootElement!!)
                }
                reformatFile(project, psiFile)
            }
            progressIndicator.isIndeterminate = false
            progressIndicator.fraction = 1.0
        }
    }

    /**
     * 创建Class
     */
    private fun createClass(
        elementFactory: PsiElementFactory,
        name: String
    ): PsiElement {
        val suffix = mObjSuffix.text.trim()
        return elementFactory.createClass("${name}${suffix}")
    }

    /**
     * 创建静态Class
     */
    private fun createStaticClass(
        elementFactory: PsiElementFactory,
        name: String,
        doc: String?
    ): PsiElement {
        val suffix = mObjSuffix.text.trim()
        val classText =
            if (doc.isNullOrEmpty() || !mCreateDoc) "public static class ${name}${suffix} {}"
            else "/**\n* $doc\n*/public static class ${name}${suffix} {}"
        return elementFactory.createClassFromText(classText, null).innerClasses[0]
    }

    private fun createJavaObjectOnJsonObject(
        factory: PsiElementFactory,
        jsonObject: JsonObject,
        parentElement: PsiElement
    ) {
        jsonObject.keySet().forEach {
            val obj = jsonObject.get(it)
            if (obj == null || obj.isJsonNull) {
                addField(factory, it, null, parentElement)
            } else if (obj.isJsonPrimitive) {
                addField(factory, it, obj as JsonPrimitive, parentElement)
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
                addFieldForObjType(factory, key, className + suffix, false, parentElement, doc)

                val lastChild = parentElement.lastChild
                val element = createStaticClass(factory, className, doc)
                createJavaObjectOnJsonObject(factory, obj as JsonObject, element)
                parentElement.addBefore(element, lastChild)
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
                        addFieldForObjType(factory, key, "any", true, parentElement, doc)
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
                                val element = createStaticClass(factory, className, doc)
                                val lastChild = parentElement.lastChild
                                createJavaObjectOnJsonObject(factory, child as JsonObject, element)
                                parentElement.addBefore(element, lastChild)
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
                        addFieldForObjType(factory, key, "any", true, parentElement, doc)
                        return@let
                    }

                    when (type) {
                        null, "null" -> {
                            addFieldForObjType(factory, key, "any", true, parentElement, doc)
                        }

                        "JsonObject" -> {
                            val suffix = mObjSuffix.text.trim()
                            addFieldForObjType(
                                factory,
                                key,
                                className + suffix,
                                true,
                                parentElement,
                                doc
                            )
                        }

                        else -> {
                            addFieldForObjType(factory, key, type, true, parentElement, doc)
                        }
                    }
                }
            }
        }
    }

    private fun addField(
        elementFactory: PsiElementFactory,
        key: String,
        jsonElement: JsonPrimitive?,
        parentElement: PsiElement
    ) {
        val type = if (jsonElement == null) {
            "Object"
        } else if (jsonElement.isBoolean) {
            "boolean"
        } else if (jsonElement.isNumber) {
            if (jsonElement.asString.contains(".")) "double" else "int"
        } else {
            "String"
        }

        val setMethod: String
        val getMethod: String
        if (type == "boolean") {
            if (key.startsWith("is")) {
                getMethod = "public boolean $key() {\n return $key; }\n"
                setMethod = "public void set${
                    StringUtils.capitalName(
                        key.replace(
                            "is",
                            ""
                        )
                    )
                }(boolean $key) {\n this.$key = $key; }\n"
            } else {
                getMethod =
                    "public boolean is${StringUtils.capitalName(key)}() {\n return $key; }\n"
                setMethod =
                    "public void set${StringUtils.capitalName(key)}(boolean $key) {\n this.$key = $key; }\n"
            }
        } else {
            getMethod = "public $type get${StringUtils.capitalName(key)}() {\n return $key; }\n"
            setMethod =
                "public void set${StringUtils.capitalName(key)}($type $key) {\n this.$key = $key; }\n"
        }

        val content = "private $type $key;\n"
        val psiField = elementFactory.createFieldFromText(content, parentElement)
        if (mCreateDoc && jsonElement != null) {
            // 不能把doc加到element，虽然此doc文档是属于element的，因为加到element下，doc结束符号'*/'与属性之间不会换行
            // 即使在'*/'后加换行'*/\n'也无效
            val doc = elementFactory.createDocCommentFromText(
                "/**\n* ${jsonElement.toString().replace("\"", "")}\n*/",
                parentElement
            )
            addFieldBeforeClass(parentElement, doc)
        }
        addFieldBeforeClass(parentElement, psiField)

        var method = elementFactory.createMethodFromText(getMethod, null, LanguageLevel.JDK_11)
        addMethodBeforeClass(parentElement, method)

        method = elementFactory.createMethodFromText(setMethod, null, LanguageLevel.JDK_11)
        addMethodBeforeClass(parentElement, method)
    }

    private fun addFieldForObjType(
        factory: PsiElementFactory,
        key: String,
        typeName: String,
        isArray: Boolean,
        parentElement: PsiElement,
        doc: String?
    ) {
        val type = if ("any" == typeName) {
            "Object"
        } else {
            if (isArray) "List<$typeName>" else typeName
        }
        val content = "private $type $key;\n"

        val getMethod = "public $type get${StringUtils.capitalName(key)}() {\n return $key; }\n"
        val setMethod =
            "public void set${StringUtils.capitalName(key)}($type $key) {\n this.$key = $key; }\n"

        if (mCreateDoc && !doc.isNullOrEmpty()) {
            // 不能把doc加到element，虽然此doc文档是属于element的，因为加到element下，doc结束符号'*/'与属性之间不会换行
            // 即使在'*/'后加换行'*/\n'也无效
            val docElement = factory.createDocCommentFromText("/**\n* $doc\n*/", parentElement)
            addFieldBeforeClass(parentElement, docElement)
        }

        val element = factory.createFieldFromText(content, parentElement)
        addFieldBeforeClass(parentElement, element)

        var method = factory.createMethodFromText(getMethod, null, LanguageLevel.JDK_11)
        addMethodBeforeClass(parentElement, method)

        method = factory.createMethodFromText(setMethod, null, LanguageLevel.JDK_11)
        addMethodBeforeClass(parentElement, method)
    }

    /**
     * 添加字段到父对象的class节点之前
     */
    private fun addFieldBeforeClass(parent: PsiElement, addChild: PsiElement) {
        var anchor: PsiElement? = null
        for (child in parent.children) {
            if (child is PsiClass || child is PsiMethod) {
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
     * 添加方法到父对象的class节点之前field节点之后
     */
    private fun addMethodBeforeClass(parent: PsiElement, addChild: PsiElement) {
        var lastPsiFieldIndex = -1
        var firstPsiClassIndex = -1
        val children = parent.children
        for (index in children.indices) {
            val child = children[index]
            if (child is PsiClass) {
                if (firstPsiClassIndex == -1) {
                    firstPsiClassIndex = index
                }
            } else if (child is PsiField || child is PsiMethod) {
                lastPsiFieldIndex = index
            }
        }

        if (lastPsiFieldIndex == -1 && firstPsiClassIndex == -1) {
            parent.addBefore(addChild, parent.lastChild)
        } else if (lastPsiFieldIndex != -1) {
            parent.addAfter(addChild, children[lastPsiFieldIndex])
        } else {
            parent.addBefore(addChild, children[firstPsiClassIndex])
        }
    }

    /**
     * 执行格式化
     *
     * @param project     项目对象
     * @param psiFile 需要格式化文件
     */
    private fun reformatFile(project: Project, psiFile: PsiFile) {
        val styleManager: JavaCodeStyleManager = JavaCodeStyleManager.getInstance(project)
        styleManager.optimizeImports(psiFile)
        styleManager.shortenClassReferences(psiFile)
        CodeStyleManager.getInstance(project).reformatText(
            psiFile,
            mutableListOf(TextRange(0, psiFile.textLength))
        )
    }
}
