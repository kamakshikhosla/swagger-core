package io.swagger.v3.core.jackson;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.ReflectionUtils;
import io.swagger.v3.oas.models.media.Schema;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public abstract class AbstractModelConverter implements ModelConverter {
    protected final ObjectMapper _mapper;
    protected final AnnotationIntrospector _intr;
    protected final TypeNameResolver _typeNameResolver = TypeNameResolver.std;
    /**
     * Minor optimization: no need to keep on resolving same types over and over
     * again.
     */
    protected Map<JavaType, String> _resolvedTypeNames = new ConcurrentHashMap<JavaType, String>();

    protected AbstractModelConverter(ObjectMapper mapper) {
        mapper.registerModule(
                new SimpleModule("swagger", Version.unknownVersion()) {
                    @Override
                    public void setupModule(SetupContext context) {
                        context.insertAnnotationIntrospector(new SwaggerAnnotationIntrospector());
                    }
                });
        _mapper = mapper;
        _intr = mapper.getSerializationConfig().getAnnotationIntrospector();

    }

    @Override
    public Schema resolve(Type type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (chain.hasNext()) {
            return chain.next().resolve(type, context, chain);
        } else {
            return null;
        }
    }

    @Override
    public Schema resolveAnnotatedType(
            Type type,
            Annotated member,
            String elementName,
            Schema parent,
            BiFunction<JavaType, Annotation[], Schema> jsonUnwrappedHandler,
            ModelConverterContext context,
            Iterator<ModelConverter> chain
    ) {
        if (chain.hasNext()) {
            return chain.next().resolveAnnotatedType(type, member, elementName, parent, jsonUnwrappedHandler, context, chain);
        } else {
            return null;
        }
    }

    @Override
    public Schema resolve(Type type, ModelConverterContext context, Annotation[] annotations, Iterator<ModelConverter> chain) {
        if (chain.hasNext()) {
            return chain.next().resolve(type, context, annotations, chain);
        } else {
            return null;
        }
    }

    protected String _typeName(JavaType type) {
        return _typeName(type, null);
    }

    protected String _typeName(JavaType type, BeanDescription beanDesc) {
        String name = _resolvedTypeNames.get(type);
        if (name != null) {
            return name;
        }
        name = _findTypeName(type, beanDesc);
        _resolvedTypeNames.put(type, name);
        return name;
    }

    protected String _findTypeName(JavaType type, BeanDescription beanDesc) {
        // First, handle container types; they require recursion
        if (type.isArrayType()) {
            return "Array";
        }

        if (type.isMapLikeType() && ReflectionUtils.isSystemType(type)) {
            return "Map";
        }

        if (type.isContainerType() && ReflectionUtils.isSystemType(type)) {
            if (Set.class.isAssignableFrom(type.getRawClass())) {
                return "Set";
            }
            return "List";
        }
        if (beanDesc == null) {
            beanDesc = _mapper.getSerializationConfig().introspectClassAnnotations(type);
        }

        PropertyName rootName = _intr.findRootName(beanDesc.getClassInfo());
        if (rootName != null && rootName.hasSimpleName()) {
            return rootName.getSimpleName();
        }
        return _typeNameResolver.nameForType(type);
    }

    protected String _typeQName(JavaType type) {
        return type.getRawClass().getName();
    }

    protected String _subTypeName(NamedType type) {
        return type.getType().getName();
    }

    protected boolean _isSetType(Class<?> cls) {
        if (cls != null) {

            if (java.util.Set.class.equals(cls)) {
                return true;
            } else {
                for (Class<?> a : cls.getInterfaces()) {
                    // this is dirty and ugly and needs to be extended into a scala model converter.  But to avoid bringing in scala runtime...
                    if (java.util.Set.class.equals(a) || "interface scala.collection.Set".equals(a.toString())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}