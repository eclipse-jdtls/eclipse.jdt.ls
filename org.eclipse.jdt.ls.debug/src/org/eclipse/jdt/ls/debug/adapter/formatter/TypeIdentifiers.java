/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.debug.adapter.formatter;

public final class TypeIdentifiers {
    public static final char ARRAY = '[';
    public static final char BYTE = 'B';
    public static final char CHAR = 'C';
    public static final char OBJECT = 'L';
    public static final char FLOAT = 'F';
    public static final char DOUBLE = 'D';
    public static final char INT = 'I';
    public static final char LONG = 'J';
    public static final char SHORT = 'S';
    public static final char BOOLEAN = 'Z';
    public static final char STRING = 's';
    public static final char THREAD = 't';
    
    public static final char THREAD_GROUP = 'g';
    public static final char CLASS_LOADER = 'l';
    public static final char CLASS_OBJECT = 'c';
    
    public static final String STRING_SIGNATURE = "Ljava/lang/String;";
    public static final String CLASS_SIGNATURE = "Ljava/lang/Class;";
}
