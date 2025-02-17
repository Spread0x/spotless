/*
 * Copyright 2022 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.spotless.glue.ktlint.compat;

import static java.util.Collections.emptySet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.pinterest.ktlint.core.KtLint;
import com.pinterest.ktlint.core.LintError;
import com.pinterest.ktlint.core.Rule;
import com.pinterest.ktlint.core.RuleProvider;
import com.pinterest.ktlint.core.api.DefaultEditorConfigProperties;
import com.pinterest.ktlint.core.api.EditorConfigDefaults;
import com.pinterest.ktlint.core.api.EditorConfigOverride;
import com.pinterest.ktlint.core.api.UsesEditorConfigProperties;
import com.pinterest.ktlint.ruleset.experimental.ExperimentalRuleSetProvider;
import com.pinterest.ktlint.ruleset.standard.StandardRuleSetProvider;

import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;

public class KtLintCompat0Dot47Dot0Adapter implements KtLintCompatAdapter {

	static class FormatterCallback implements Function2<LintError, Boolean, Unit> {
		@Override
		public Unit invoke(LintError lint, Boolean corrected) {
			if (!corrected) {
				KtLintCompatReporting.report(lint.getLine(), lint.getCol(), lint.getRuleId(), lint.getDetail());
			}
			return null;
		}
	}

	@Override
	public String format(final String text, final String name, final boolean isScript,
			final boolean useExperimental,
			final Map<String, String> userData,
			final Map<String, Object> editorConfigOverrideMap) {
		final FormatterCallback formatterCallback = new FormatterCallback();

		Set<RuleProvider> allRuleProviders = new LinkedHashSet<>(
				new StandardRuleSetProvider().getRuleProviders());
		if (useExperimental) {
			allRuleProviders.addAll(new ExperimentalRuleSetProvider().getRuleProviders());
		}

		EditorConfigOverride editorConfigOverride;
		if (editorConfigOverrideMap.isEmpty()) {
			editorConfigOverride = EditorConfigOverride.Companion.getEmptyEditorConfigOverride();
		} else {
			editorConfigOverride = createEditorConfigOverride(allRuleProviders.stream().map(
					RuleProvider::createNewRuleInstance).collect(
							Collectors.toList()),
					editorConfigOverrideMap);
		}

		return KtLint.INSTANCE.format(new KtLint.ExperimentalParams(
				name,
				text,
				emptySet(),
				allRuleProviders,
				userData,
				formatterCallback,
				isScript,
				null,
				false,
				EditorConfigDefaults.Companion.getEmptyEditorConfigDefaults(),
				editorConfigOverride,
				false));
	}

	/**
	 * Create EditorConfigOverride from user provided parameters.
	 * Calling this method requires KtLint 0.45.2.
	 */
	private static EditorConfigOverride createEditorConfigOverride(final List<Rule> rules, Map<String, Object> editorConfigOverrideMap) {
		// Get properties from rules in the rule sets
		Stream<UsesEditorConfigProperties.EditorConfigProperty<?>> ruleProperties = rules.stream()
				.filter(rule -> rule instanceof UsesEditorConfigProperties)
				.flatMap(rule -> ((UsesEditorConfigProperties) rule).getEditorConfigProperties().stream());

		// Create a mapping of properties to their names based on rule properties and default properties
		Map<String, UsesEditorConfigProperties.EditorConfigProperty<?>> supportedProperties = Stream
				.concat(ruleProperties, DefaultEditorConfigProperties.INSTANCE.getEditorConfigProperties().stream())
				.distinct()
				.collect(Collectors.toMap(property -> property.getType().getName(), property -> property));

		// Create config properties based on provided property names and values
		@SuppressWarnings("unchecked")
		Pair<UsesEditorConfigProperties.EditorConfigProperty<?>, ?>[] properties = editorConfigOverrideMap.entrySet().stream()
				.map(entry -> {
					UsesEditorConfigProperties.EditorConfigProperty<?> property = supportedProperties.get(entry.getKey());
					if (property != null) {
						return new Pair<>(property, entry.getValue());
					} else {
						return null;
					}
				})
				.filter(Objects::nonNull)
				.toArray(Pair[]::new);

		return EditorConfigOverride.Companion.from(properties);
	}
}
