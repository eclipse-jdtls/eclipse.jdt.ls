/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.semantictokens;

public enum TokenType {
    COMMENT("comment"),
    KEYWORD("keyword"),
    STRING("string"),
    NUMBER("number"),
    REGEXP("regexp"),
    OPERATOR("operator"),
    NAMESPACE("namespace"),
    TYPE("type"),
    STRUCT("struct"),
    CLASS("class"),
    INTERFACE("interface"),
    ENUM("enum"),
    TYPE_PARAMETER("typeParameter"),
    FUNCTION("function"),
    MEMBER("member"),
    PROPERTY("property"),
    MACRO("macro"),
    VARIABLE("variable"),
    PARAMETER("parameter"),
    LABEL("label")
    ;

    private String literalString;
    TokenType(String tokenTypeString) {
        this.literalString = tokenTypeString;
    }

    public String toString() {
        return this.literalString;
    }
}
