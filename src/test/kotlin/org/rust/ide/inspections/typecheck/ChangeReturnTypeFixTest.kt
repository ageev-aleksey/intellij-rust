/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.typecheck

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeCheckInspection

class ChangeReturnTypeFixTest : RsInspectionsTestBase(RsTypeCheckInspection::class) {

    fun `test str vs () in function`() = checkFixByText("Change return type of function 'foo' to '&str'", """
        fn foo() {
            <error>"Hello World!"<caret></error>
        }
    """, """
        fn foo() -> &'static str {
            "Hello World!"
        }
    """)

    fun `test return str vs () in function`() = checkFixByText("Change return type of function 'foo' to '&str'", """
        fn foo() {
            return; <error>"Hello World!"<caret></error>;
        }
    """, """
        fn foo() -> &'static str {
            return "Hello World!";
        }
    """)

    fun `test str vs i32 in function`() = checkFixByText("Change return type of function 'foo' to '&str'", """
        fn foo() -> i32 {
            <error>"Hello World!"<caret></error>
        }
    """, """
        fn foo() -> &'static str {
            "Hello World!"
        }
    """)

    fun `test return str vs i32 in function`() = checkFixByText("Change return type of function 'foo' to '&str'", """
        fn foo() -> i32 {
            return <error>"Hello World!"<caret></error>;
        }
    """, """
        fn foo() -> &'static str {
            return "Hello World!";
        }
    """)

    fun `test str vs i32 in closure`() = checkFixByText("Change return type of closure to '&str'", """
        fn foo() {
            let _ = || -> i32 <error>"Hello World!"<caret></error>;
        }
    """, """
        fn foo() {
            let _ = || -> &'static str "Hello World!";
        }
    """)

    fun `test return str vs i32 in closure`() = checkFixByText("Change return type of closure to '&str'", """
        fn foo() {
            let _ = || -> i32 { return; <error>"Hello World!"<caret></error>; };
        }
    """, """
        fn foo() {
            let _ = || -> &'static str { return "Hello World!"; };
        }
    """)

    fun `test str vs i32 in method`() = checkFixByText("Change return type of method 'foo' to '&str'", """
        struct S;
        impl S {
            fn foo(&self) -> i32 {
                <error>"Hello World!"<caret></error>
            }
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) -> &'static str {
                "Hello World!"
            }
        }
    """)

    fun `test alias`() = checkFixByText("Change return type of function 'foo' to 'A'", """
        struct S;
        type A = S;
        fn foo(a: A) {
            <error>a<caret></error>
        }
    """, """
        struct S;
        type A = S;
        fn foo(a: A) -> A {
            a
        }
    """)
}
