package com.kitsune.core.network.provider

/** Thrown instead of attempting a call when no provider is configured, or a model selection points
 * at a provider that has since been removed. */
class NoProviderConfiguredException :
    Exception("Aucun fournisseur d'IA configuré. Ajoutez votre clé API dans Réglages → Fournisseurs d'IA.")
