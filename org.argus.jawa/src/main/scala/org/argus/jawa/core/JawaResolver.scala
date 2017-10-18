/*
 * Copyright (c) 2017. Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Detailed contributors are listed in the CONTRIBUTOR.md
 */

package org.argus.jawa.core

import org.argus.jawa.ast.{CompilationUnit, MethodDeclaration}
import org.argus.jawa.compiler.parser.JawaParser
import org.argus.jawa.core.frontend.{MyClass, MyMethod}
import org.argus.jawa.core.frontend.sourcefile.SourcefileParser
import org.argus.jawa.core.util._

/**
 * this object collects info from the symbol table and builds Center, JawaClass, and JawaMethod
 *
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 */
trait JawaResolver extends JavaKnowledge { self: Global =>
  
  import JawaResolver._
  
//  private val DEBUG: Boolean = false
  private final val TITLE: String = "JawaResolver"
  
  /**
   * resolve the given method code. Normally only for dummyMain i.e. environment method
   */
  def resolveMethodCode(sig: Signature, code: String): JawaMethod = {
    val md = parseMethod(code, reporter)
    val method = SourcefileParser.resolveMethod(md)
    val clazz = getClazz(sig.getClassType) match {
      case Some(c) => c
      case None => resolveClass(sig.getClassType, allowUnknown = false)
    }
    resolveFromMyMethod(clazz, method)
  }
    
  /**
   * resolve the given classes.
   */
  protected[core] def resolveClass(classType: JawaType, allowUnknown: Boolean): JawaClass = {
    val clazz = if(!classType.isArray && !containsClassFile(classType)) {
      if(!allowUnknown) throw JawaResolverError("Does not find class " + classType + " and don't allow unknown.")
      val rec = new JawaClass(this, classType, "")
      rec.setUnknown()
      if(classType.baseType.unknown) {
        val baseCls = getClassOrResolve(classType.removeUnknown())
        if(baseCls.isInterface) {
          if(rec.getType != JAVA_TOPLEVEL_OBJECT_TYPE)
            rec.setSuperClass(getClassOrResolve(JAVA_TOPLEVEL_OBJECT_TYPE))
          rec.addInterface(baseCls)
        } else {
          rec.setSuperClass(baseCls)
        }
      } else {
        if(rec.getType != JAVA_TOPLEVEL_OBJECT_TYPE)
          rec.setSuperClass(getClassOrResolve(JAVA_TOPLEVEL_OBJECT_TYPE))
        reporter.echo(TITLE, "Add phantom class " + rec)
        addClassNotFound(classType)
      }
      rec
    } else {
      forceResolve(classType)
    }
    if(!getClassHierarchy.resolved(clazz)) {
      addClassesNeedUpdateInHierarchy(clazz)
    }
    clazz
  }
  
  /**
   * force resolve the given class to hierarchy level
   */
  private def forceResolve(classType: JawaType): JawaClass = {
    val clazz = if(classType.isArray){
      resolveArrayClass(classType)
    } else {
      val mc = getMyClass(classType).get
      resolveFromMyClass(mc)
    }
    clazz
  }

  /**
   * resolve array class
   */
  private def resolveArrayClass(typ: JawaType): JawaClass = {
    val recAccessFlag =
      if(isJavaPrimitive(typ.baseTyp)){
        "FINAL_PUBLIC"
      } else {
        val base = resolveClass(new JawaType(typ.baseType), allowUnknown = true)
        val baseaf = base.getAccessFlagsStr
        if(baseaf.contains("FINAL")) baseaf else "FINAL_" + baseaf
      }
    val clazz: JawaClass = new JawaClass(this, typ, recAccessFlag)
    clazz.setSuperClass(getClassOrResolve(JAVA_TOPLEVEL_OBJECT_TYPE))
    new JawaField(clazz, "class", new JawaType("java.lang.Class"), "FINAL_STATIC")
    new JawaField(clazz, "length", new JawaType("int"), "FINAL")
    clazz
  }
    
  protected def resolveFromMyClass(mc: MyClass): JawaClass = {
    val typ = mc.typ
    val accessFlag = mc.accessFlag
    val clazz: JawaClass = JawaClass(this, typ, accessFlag)
    mc.fields foreach{ f =>
      val fname = f.FQN.fieldName
      val ftyp = f.FQN.typ
      val faccessFlag = f.accessFlag
      JawaField(clazz, fname, ftyp, faccessFlag)
    }
    mc.methods foreach { m =>
      resolveFromMyMethod(clazz, m)
    }
    mc.superType match {
      case Some(t) => clazz.setSuperClass(getClassOrResolve(t))
      case None =>
    }
    mc.interfaces.foreach(i => clazz.addInterface(getClassOrResolve(i)))
    clazz
  }
  
  protected def resolveFromMyMethod(clazz: JawaClass, m: MyMethod): JawaMethod = {
    val sig = m.signature
    val mname = sig.methodName
    val thisOpt: Option[String] = m.thisParam
    val paramNames = m.params
    val paramsize = paramNames.size
    val params: MList[(String, JawaType)] = mlistEmpty
    val paramtyps = sig.getParameterTypes
    for(i <- 0 until paramsize){
      val pname = paramNames(i)
      val paramtyp = paramtyps(i)
      params += ((pname, paramtyp))
    }
    val retTyp = sig.getReturnType
    val accessFlag = m.accessFlag
    
    val method = JawaMethod(clazz, mname, thisOpt, params.toList, retTyp, accessFlag)
    m.body.foreach(method.setBody)
    method
  }
}

object JawaResolver{
  def parseClass(code: String, reporter: Reporter): CompilationUnit = {
    val model = JawaParser.parse[CompilationUnit](Left(code), resolveBody = false, reporter, classOf[CompilationUnit])
    model match{case Left(m) => m; case Right(e) => throw e}
  }
  def parseMethod(code: String, reporter: Reporter): MethodDeclaration = {
    val md = JawaParser.parse[MethodDeclaration](Left(code), resolveBody = false, reporter, classOf[MethodDeclaration])
    md match{case Left(m) => m; case Right(e) => throw e}
  }
}