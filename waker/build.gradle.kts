plugins {
    id("blockcraft.java-conventions")
}

// A plain runnable jar without dependencies; compose runs it on a stock JRE image (README, "Server discovery and groups").
tasks.jar {
    manifest {
        attributes("Main-Class" to "me.blockcraft.waker.Waker")
    }
}
