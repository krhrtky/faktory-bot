package com.example.faktory.transaction

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class FactoryTransactionExtension : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        TransactionManager.getInstance().begin()
    }

    override fun afterEach(context: ExtensionContext) {
        TransactionManager.getInstance().rollback()
    }
}
