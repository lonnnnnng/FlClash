SHELL := /bin/bash

.PHONY: help submodules

help:
	@echo 'make submodules  # initialize the Mihomo core submodule'
	@echo './gradlew :app:assembleDebug  # build the Android Kotlin app'
	@echo './gradlew test :app:lintDebug  # run JVM tests and Android lint'

submodules:
	git submodule update --init --recursive
