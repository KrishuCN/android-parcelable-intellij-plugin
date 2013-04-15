package pl.charmas.parcelablegenerator;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import pl.charmas.parcelablegenerator.typeserializers.ChainSerializerFactory;
import pl.charmas.parcelablegenerator.typeserializers.PrimitiveTypeSerializerFactory;
import pl.charmas.parcelablegenerator.typeserializers.TypeSerializer;
import pl.charmas.parcelablegenerator.typeserializers.TypeSerializerFactory;

import java.util.List;

public class CodeGenerator {

    private final PsiClass psiClass;
    private final List<PsiField> fields;
    private final TypeSerializerFactory typeSerializerFactory;

    public CodeGenerator(PsiClass psiClass, List<PsiField> fields) {
        this.psiClass = psiClass;
        this.fields = fields;
        this.typeSerializerFactory = new ChainSerializerFactory(new PrimitiveTypeSerializerFactory());
    }

    private String generateStaticCreator(PsiClass psiClass) {
        StringBuilder sb = new StringBuilder("public static android.os.Parcelable.Creator<");
        String name = psiClass.getName();
        sb.append(name).append("> CREATOR = new android.os.Parcelable.Creator<").append(name).append(">(){")
                .append("public ").append(name).append(" createFromParcel(android.os.Parcel source) {")
                .append("return new ").append(name).append("(source);}")
                .append("public ").append(name).append("[] newArray(int size) {")
                .append("return new ").append(name).append("[size];}")
                .append("};");
        return sb.toString();
    }

    private String generateConstructor(List<PsiField> fields, PsiClass psiClass) {
        StringBuilder sb = new StringBuilder("private ").append(psiClass.getName()).append("(android.os.Parcel in) {");
        for (PsiField field : fields) {
            sb.append(getSerializerForType(field).readValue(field, "in"));
        }
        sb.append("}");
        return sb.toString();
    }


    private String generateWriteToParcel(List<PsiField> fields) {
        StringBuilder sb = new StringBuilder("@Override public void writeToParcel(android.os.Parcel dest, int flags) {");
        for (PsiField field : fields) {
            sb.append(getSerializerForType(field).writeValue(field, "dest", "flags"));
        }
        sb.append("}");
        return sb.toString();
    }

    private TypeSerializer getSerializerForType(PsiField field) {
        return typeSerializerFactory.getSerializer(field.getType());
    }

    private String generateDescribeContents() {
        return "@Override public int describeContents() { return 0; }";
    }

    public void generate() {
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
        PsiMethod describeContentsMethod = elementFactory.createMethodFromText(generateDescribeContents(), psiClass);
        PsiMethod writeToParcelMethod = elementFactory.createMethodFromText(generateWriteToParcel(fields), psiClass);
        PsiMethod constructor = elementFactory.createMethodFromText(generateConstructor(fields, psiClass), psiClass);
        PsiField creatorField = elementFactory.createFieldFromText(generateStaticCreator(psiClass), psiClass);

        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(psiClass.getProject());
        styleManager.shortenClassReferences(psiClass.addBefore(describeContentsMethod, psiClass.getLastChild()));
        styleManager.shortenClassReferences(psiClass.addBefore(writeToParcelMethod, psiClass.getLastChild()));
        styleManager.shortenClassReferences(psiClass.addBefore(constructor, psiClass.getLastChild()));
        styleManager.shortenClassReferences(psiClass.addBefore(creatorField, psiClass.getLastChild()));
        makeClassImplementParcelable(elementFactory);
    }

    private void makeClassImplementParcelable(PsiElementFactory elementFactory) {
        PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();
        for (PsiClassType implementsListType : implementsListTypes) {
            PsiClass resolved = implementsListType.resolve();
            if (resolved != null && "android.os.Parcelable".equals(resolved.getQualifiedName())) {
                return;
            }
        }

        String implementsType = "android.os.Parcelable";
        PsiJavaCodeReferenceElement implementsReference = elementFactory.createReferenceFromText(implementsType, psiClass);
        PsiReferenceList implementsList = psiClass.getImplementsList();
        if (implementsList != null) {
            implementsList.add(implementsReference);
        }
    }

}
