package org.sample;

/**
 * RegionFoldingRange
 */
public class RegionFoldingRange {

    // region region1
    void foo() {

    }

    void bar() {

    }
    // endregion

    // <editor-fold desc="region3">
        // #region region1

    //#endregion


    // </editor-fold>
}