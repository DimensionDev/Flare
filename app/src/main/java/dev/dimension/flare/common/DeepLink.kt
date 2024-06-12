package dev.dimension.flare.common

import com.ramcosta.composedestinations.spec.Direction

fun Direction.deeplink(): String = "$APPSCHEMA://${this.route}"
