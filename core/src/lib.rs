//! Sotto's local writing brain.
//!
//! Everything in this crate runs on the device. It has no Android dependencies
//! of its own; the optional `jni-bridge` feature only exposes the same pure
//! functions to the Java shell.

use regex::{Regex, RegexBuilder};

pub const VERSION: &str = "1.0.0";

// ---------------------------------------------------------------- public API

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Style {
    Default,
    Formal,
    Casual,
    VeryCasual,
    Excited,
    Notes,
}

impl Style {
    pub fn from_key(key: &str) -> Style {
        match key.trim().to_ascii_lowercase().as_str() {
            "formal" => Style::Formal,
            "casual" => Style::Casual,
            "very_casual" | "very casual" => Style::VeryCasual,
            "excited" => Style::Excited,
            "notes" => Style::Notes,
            _ => Style::Default,
        }
    }

    pub fn key(self) -> &'static str {
        match self {
            Style::Default => "default",
            Style::Formal => "formal",
            Style::Casual => "casual",
            Style::VeryCasual => "very_casual",
            Style::Excited => "excited",
            Style::Notes => "notes",
        }
    }

    /// Pick a sensible default from the package the user is typing into.
    pub fn infer_for_package(package: &str) -> Style {
        let p = package.to_ascii_lowercase();
        if contains_any(&p, &["gmail", "android.gm", "outlook", "mail", "spark", "proton", "superhuman"]) {
            Style::Formal
        } else if contains_any(
            &p,
            &["whatsapp", "telegram", "signal", "messenger", "discord", "slack", "sms", "messages"],
        ) {
            Style::Casual
        } else if contains_any(&p, &["notes", "notion", "obsidian", "keep", "bear", "logseq"]) {
            Style::Notes
        } else {
            Style::Default
        }
    }
}

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Command {
    None = 0,
    Undo = 1,
    NewParagraph = 2,
    NewLine = 3,
    Send = 4,
    SelectAll = 5,
    Copy = 6,
    Cut = 7,
    Paste = 8,
    Stop = 9,
    Clear = 10,
    RestyleFormal = 11,
    RestyleCasual = 12,
    RestyleExcited = 13,
    RestyleNotes = 14,
}

pub const COMMAND_NAMES: [&str; 15] = [
    "none",
    "undo",
    "new_paragraph",
    "new_line",
    "send",
    "select_all",
    "copy",
    "cut",
    "paste",
    "stop",
    "clear",
    "restyle_formal",
    "restyle_casual",
    "restyle_excited",
    "restyle_notes",
];

impl Command {
    pub fn name(self) -> &'static str {
        COMMAND_NAMES[self as usize]
    }
}

#[derive(Clone, Debug)]
pub struct Options {
    pub style: Style,
    pub package: String,
    pub remove_fillers: bool,
    pub spoken_punctuation: bool,
    pub auto_capitalize: bool,
    pub trailing_space: bool,
    pub adapt_tone: bool,
    pub snippets: Vec<(String, String)>,
    pub dictionary: Vec<(String, String)>,
}

impl Default for Options {
    fn default() -> Self {
        Options {
            style: Style::Default,
            package: String::new(),
            remove_fillers: true,
            spoken_punctuation: true,
            auto_capitalize: true,
            trailing_space: true,
            adapt_tone: true,
            snippets: Vec::new(),
            dictionary: Vec::new(),
        }
    }
}

#[derive(Clone, Debug)]
pub struct Outcome {
    pub text: String,
    pub style: Style,
    pub command: Command,
    pub words: usize,
    pub changes: usize,
}

/// Detect a short spoken command. Commands are only recognised when the whole
/// utterance is at most four words, so "send it" in the middle of a sentence is
/// left alone.
pub fn detect_command(input: &str) -> Command {
    let cleaned = input
        .to_ascii_lowercase()
        .chars()
        .map(|c| if c.is_alphanumeric() || c == ' ' { c } else { ' ' })
        .collect::<String>();
    let words: Vec<&str> = cleaned.split_whitespace().collect();
    if words.is_empty() || words.len() > 4 {
        return Command::None;
    }
    let phrase = words.join(" ");
    match phrase.as_str() {
        "scratch that" | "undo that" | "delete that" | "undo" => Command::Undo,
        "new paragraph" | "next paragraph" => Command::NewParagraph,
        "new line" | "next line" => Command::NewLine,
        "send it" | "send message" | "send" => Command::Send,
        "select all" => Command::SelectAll,
        "copy that" | "copy" => Command::Copy,
        "cut that" | "cut" => Command::Cut,
        "paste that" | "paste" => Command::Paste,
        "stop listening" | "stop dictation" | "stop" => Command::Stop,
        "clear all" | "clear that" | "clear" => Command::Clear,
        "make it formal" | "formal" => Command::RestyleFormal,
        "make it casual" | "casual" => Command::RestyleCasual,
        "make it excited" | "excited" => Command::RestyleExcited,
        "make it notes" | "notes" | "bullet points" => Command::RestyleNotes,
        _ => Command::None,
    }
}

// ------------------------------------------------------------------ pipeline

struct Protected {
    text: String,
    spans: Vec<String>,
}

/// Replace URLs, e-mail addresses, times and decimals with private-use
/// sentinels so later punctuation and casing passes cannot damage them.
fn protect(input: &str) -> Protected {
    let patterns = [
        r"(?i)\b(?:https?://|www\.)[^\s]+",
        r"(?i)\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b",
        r"\b\d{1,2}:\d{2}(?::\d{2})?\b",
        r"\b\d+\.\d+\b",
    ];
    let mut text = input.to_string();
    let mut spans: Vec<String> = Vec::new();
    for pattern in patterns {
        let re = Regex::new(pattern).expect("static pattern");
        let mut out = String::with_capacity(text.len());
        let mut last = 0usize;
        for m in re.find_iter(&text) {
            out.push_str(&text[last..m.start()]);
            out.push('\u{E000}');
            out.push_str(&spans.len().to_string());
            out.push('\u{E001}');
            spans.push(m.as_str().to_string());
            last = m.end();
        }
        out.push_str(&text[last..]);
        text = out;
    }
    Protected { text, spans }
}

fn restore(mut text: String, spans: &[String]) -> String {
    for (i, span) in spans.iter().enumerate() {
        let token = format!("\u{E000}{}\u{E001}", i);
        if text.contains(&token) {
            text = text.replace(&token, span);
        }
    }
    text
}

fn collapse_spaces(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let mut last_space = false;
    for c in input.chars() {
        if c == '\n' {
            out.push('\n');
            last_space = false;
        } else if c == ' ' || c == '\t' {
            if !last_space {
                out.push(' ');
                last_space = true;
            }
        } else {
            out.push(c);
            last_space = false;
        }
    }
    out
}

/// Case-insensitive, whitespace-tolerant phrase replacement.
fn replace_phrase(input: &str, phrase: &str, replacement: &str) -> (String, usize) {
    if phrase.trim().is_empty() {
        return (input.to_string(), 0);
    }
    let escaped = regex::escape(phrase.trim());
    let pattern = format!(r"(?i)\b{}\b", escaped.replace(r"\ ", r"\s+"));
    let re = match RegexBuilder::new(&pattern).case_insensitive(true).build() {
        Ok(r) => r,
        Err(_) => return (input.to_string(), 0),
    };
    let count = re.find_iter(input).count();
    if count == 0 {
        return (input.to_string(), 0);
    }
    (re.replace_all(input, replacement).into_owned(), count)
}

const SPOKEN: [(&str, &str); 24] = [
    ("question mark", "?"),
    ("exclamation mark", "!"),
    ("exclamation point", "!"),
    ("full stop", "."),
    ("period", "."),
    ("semicolon", ";"),
    ("semi colon", ";"),
    ("colon", ":"),
    ("comma", ","),
    ("ellipsis", "..."),
    ("open parenthesis", "("),
    ("close parenthesis", ")"),
    ("open quote", "\""),
    ("close quote", "\""),
    ("ampersand", "&"),
    ("percent sign", "%"),
    ("at sign", "@"),
    ("forward slash", "/"),
    ("backslash", "\\"),
    ("hyphen", "-"),
    ("dash", " - "),
    ("new line", "\n"),
    ("new paragraph", "\n\n"),
    ("plus sign", "+"),
];

fn spoken_punctuation(input: &str) -> (String, usize) {
    let mut text = input.to_string();
    let mut changes = 0usize;
    for (phrase, mark) in SPOKEN {
        let (next, n) = replace_phrase(&text, phrase, &format!(" {} ", mark.trim()));
        if n > 0 {
            text = next;
            changes += n;
        }
    }
    (text, changes)
}

const FILLERS: [&str; 7] = ["um", "uh", "erm", "ah", "hmm", "you know", "i mean"];

fn remove_fillers(input: &str) -> (String, usize) {
    let mut text = input.to_string();
    let mut changes = 0usize;
    for filler in FILLERS {
        let re = RegexBuilder::new(&format!(r"(?i)\b{}\b", regex::escape(filler)))
            .build()
            .expect("static filler");
        let n = re.find_iter(&text).count();
        if n > 0 {
            text = re.replace_all(&text, "").into_owned();
            changes += n;
        }
    }
    (collapse_spaces(text.trim()), changes)
}

fn apply_pairs(input: &str, pairs: &[(String, String)]) -> (String, usize) {
    let mut text = input.to_string();
    let mut changes = 0usize;
    for (from, to) in pairs {
        if from.trim().is_empty() {
            continue;
        }
        let (next, n) = replace_phrase(&text, from, to);
        if n > 0 {
            text = next;
            changes += n;
        }
    }
    (text, changes)
}

/// Fix spacing before punctuation and duplicated marks.
fn normalize_punctuation(input: &str) -> String {
    let mut text = input.to_string();
    let fixes = [
        (r"\s+([,.;:!?])", "$1"),
        (r"([,.;:!?])([,.;:!?]+)", "$1"),
        (r"\(\s+", "("),
        (r"\s+\)", ")"),
        (r"\s+'\s*", "'"),
    ];
    for (pattern, replacement) in fixes {
        if let Ok(re) = Regex::new(pattern) {
            text = re.replace_all(&text, replacement).into_owned();
        }
    }
    text
}

fn capitalize_after(text: &str, mark: char) -> String {
    let mut out = String::with_capacity(text.len());
    let mut upper = false;
    for c in text.chars() {
        if upper && c.is_alphabetic() {
            for u in c.to_uppercase() {
                out.push(u);
            }
            upper = false;
        } else {
            out.push(c);
            if c == mark {
                upper = true;
            }
        }
    }
    out
}

fn auto_capitalize(input: &str) -> String {
    let mut text = input.to_string();
    text = capitalize_after(&text, '.');
    text = capitalize_after(&text, '!');
    text = capitalize_after(&text, '?');
    text = capitalize_after(&text, '\n');
    let mut chars: Vec<char> = text.chars().collect();
    if let Some(first) = chars.iter_mut().find(|c| c.is_alphabetic()) {
        for u in first.to_uppercase() {
            *first = u;
        }
    }
    chars.into_iter().collect()
}

const CONTRACTIONS: [(&str, &str); 18] = [
    ("don't", "do not"),
    ("doesn't", "does not"),
    ("didn't", "did not"),
    ("can't", "cannot"),
    ("won't", "will not"),
    ("isn't", "is not"),
    ("aren't", "are not"),
    ("wasn't", "was not"),
    ("weren't", "were not"),
    ("haven't", "have not"),
    ("hasn't", "has not"),
    ("hadn't", "had not"),
    ("wouldn't", "would not"),
    ("shouldn't", "should not"),
    ("couldn't", "could not"),
    ("i'm", "I am"),
    ("it's", "it is"),
    ("we're", "we are"),
];

fn apply_style(text: &str, style: Style) -> String {
    match style {
        Style::Default => text.to_string(),
        Style::Formal => {
            let mut out = text.to_string();
            for (from, to) in CONTRACTIONS {
                let re = RegexBuilder::new(&format!(r"(?i)\b{}\b", regex::escape(from)))
                    .build()
                    .expect("static contraction");
                out = re.replace_all(&out, to).into_owned();
            }
            if let Ok(re) = Regex::new(r"[!]+") {
                out = re.replace_all(&out, ".").into_owned();
            }
            out
        }
        Style::Casual => text.replace('!', "."),
        Style::VeryCasual => {
            let mut out = text.to_lowercase();
            if let Ok(re) = Regex::new(r"[.!?]+(\s|$)") {
                out = re.replace_all(&out, "$1").into_owned();
            }
            out
        }
        Style::Excited => {
            let mut out = text.to_string();
            if let Ok(re) = Regex::new(r"\.(\s|$)") {
                out = re.replace_all(&out, "!$1").into_owned();
            }
            if let Ok(re) = Regex::new(r"!+") {
                out = re.replace_all(&out, "!").into_owned();
            }
            auto_capitalize(&out)
        }
        Style::Notes => {
            let mut out = String::new();
            for line in text.split('\n') {
                let trimmed = line.trim();
                if trimmed.is_empty() {
                    continue;
                }
                for sentence in split_sentences(trimmed) {
                    if !out.is_empty() {
                        out.push('\n');
                    }
                    out.push_str("\u{2022} ");
                    out.push_str(sentence.trim());
                }
            }
            out
        }
    }
}

fn split_sentences(text: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut current = String::new();
    for c in text.chars() {
        current.push(c);
        if c == '.' || c == '!' || c == '?' {
            out.push(current.trim().to_string());
            current.clear();
        }
    }
    if !current.trim().is_empty() {
        out.push(current.trim().to_string());
    }
    out
}

fn finish_sentence(mut text: String) -> String {
    let trimmed = text.trim_end().to_string();
    text = trimmed;
    if text.is_empty() {
        return text;
    }
    let last = text.chars().last().unwrap_or(' ');
    if !matches!(last, '.' | '!' | '?' | ':' | ';' | ')' | ']' | '"' | '\u{2026}') {
        text.push('.');
    }
    text
}

fn count_words(input: &str) -> usize {
    input.split_whitespace().filter(|w| w.chars().any(|c| c.is_alphanumeric())).count()
}

// -------------------------------------------------------------- entry points

pub fn process(input: &str, opts: &Options) -> Outcome {
    let command = detect_command(input);
    if command != Command::None {
        return Outcome {
            text: input.trim().to_string(),
            style: opts.style,
            command,
            words: count_words(input),
            changes: 0,
        };
    }

    let mut changes = 0usize;
    let protected = protect(input);
    let mut text = protected.text;

    text = collapse_spaces(&text);

    if opts.spoken_punctuation {
        let (next, n) = spoken_punctuation(&text);
        text = next;
        changes += n;
    }

    if !opts.snippets.is_empty() {
        let (next, n) = apply_pairs(&text, &opts.snippets);
        text = next;
        changes += n;
    }
    if !opts.dictionary.is_empty() {
        let (next, n) = apply_pairs(&text, &opts.dictionary);
        text = next;
        changes += n;
    }

    if opts.remove_fillers {
        let (next, n) = remove_fillers(&text);
        text = next;
        changes += n;
    }

    text = normalize_punctuation(&text);

    let effective_style = if opts.adapt_tone && opts.style == Style::Default {
        Style::infer_for_package(&opts.package)
    } else {
        opts.style
    };

    text = finish_sentence(text);
    text = apply_style(&text, effective_style);

    if opts.auto_capitalize && effective_style != Style::VeryCasual {
        text = auto_capitalize(&text);
        if let Ok(re) = Regex::new(r"(?i)\bi\b") {
            text = re.replace_all(&text, "I").into_owned();
        }
    }

    text = restore(text, &protected.spans);
    text = collapse_spaces(text.trim());

    if opts.trailing_space && !text.is_empty() {
        text.push(' ');
    }

    Outcome {
        words: count_words(&text),
        changes,
        style: effective_style,
        command: Command::None,
        text,
    }
}

/// Parse the compact map encoding used across the JNI boundary.
/// Pairs are separated by `\u{1e}` and key/value by `\u{1f}`.
pub fn parse_pairs(raw: &str) -> Vec<(String, String)> {
    raw.split('\u{1e}')
        .filter_map(|pair| {
            let mut parts = pair.splitn(2, '\u{1f}');
            let key = parts.next()?.trim();
            let value = parts.next().unwrap_or("");
            if key.is_empty() {
                None
            } else {
                Some((key.to_string(), value.to_string()))
            }
        })
        .collect()
}

fn contains_any(haystack: &str, needles: &[&str]) -> bool {
    needles.iter().any(|n| haystack.contains(n))
}

// ----------------------------------------------------------------------- jni

#[cfg(feature = "jni-bridge")]
mod bridge {
    use super::*;
    use jni::objects::{JClass, JString};
    use jni::sys::{jboolean, jint, jstring};
    use jni::JNIEnv;
    use std::ptr;

    fn read(env: &mut JNIEnv, value: &JString) -> String {
        env.get_string(value)
            .map(|s| s.into())
            .unwrap_or_else(|_| String::new())
    }

    fn ret(env: &mut JNIEnv, value: String) -> jstring {
        env.new_string(value)
            .map(|s| s.into_raw())
            .unwrap_or(ptr::null_mut())
    }

    #[no_mangle]
    pub extern "system" fn Java_app_sotto_voice_core_NativeCore_version<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
    ) -> jstring {
        ret(&mut env, format!("sotto-core {}", VERSION))
    }

    #[no_mangle]
    pub extern "system" fn Java_app_sotto_voice_core_NativeCore_command<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        input: JString<'l>,
    ) -> jint {
        let text = read(&mut env, &input);
        detect_command(&text) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_app_sotto_voice_core_NativeCore_countWords<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        input: JString<'l>,
    ) -> jint {
        let text = read(&mut env, &input);
        count_words(&text) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_app_sotto_voice_core_NativeCore_process<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        input: JString<'l>,
        style_key: JString<'l>,
        package: JString<'l>,
        remove_fillers: jboolean,
        spoken_punctuation: jboolean,
        auto_capitalize: jboolean,
        trailing_space: jboolean,
        adapt_tone: jboolean,
        snippets_raw: JString<'l>,
        dictionary_raw: JString<'l>,
    ) -> jstring {
        let text = read(&mut env, &input);
        let style = Style::from_key(&read(&mut env, &style_key));
        let package = read(&mut env, &package);
        let snippets = parse_pairs(&read(&mut env, &snippets_raw));
        let dictionary = parse_pairs(&read(&mut env, &dictionary_raw));

        let opts = Options {
            style,
            package,
            remove_fillers: remove_fillers != 0,
            spoken_punctuation: spoken_punctuation != 0,
            auto_capitalize: auto_capitalize != 0,
            trailing_space: trailing_space != 0,
            adapt_tone: adapt_tone != 0,
            snippets,
            dictionary,
        };

        ret(&mut env, process(&text, &opts).text)
    }
}

// --------------------------------------------------------------------- tests

#[cfg(test)]
mod tests {
    use super::*;

    fn opts(style: Style) -> Options {
        Options {
            style,
            trailing_space: false,
            ..Options::default()
        }
    }

    #[test]
    fn cleans_fillers_and_capitalizes() {
        let out = process("um so i think we should uh ship it", &opts(Style::Default));
        assert_eq!(out.text, "So I think we should ship it.");
    }

    #[test]
    fn spoken_punctuation() {
        let out = process(
            "hello comma how are you question mark i am fine period",
            &opts(Style::Default),
        );
        assert_eq!(out.text, "Hello, how are you? I am fine.");
    }

    #[test]
    fn protects_urls_and_decimals() {
        let out = process(
            "go to https://example.com/a.b and pay 12.50 dollars",
            &opts(Style::Default),
        );
        assert!(out.text.contains("https://example.com/a.b"));
        assert!(out.text.contains("12.50"));
    }

    #[test]
    fn snippets_and_dictionary() {
        let mut o = opts(Style::Default);
        o.snippets = vec![("my addr".into(), "221B Baker Street".into())];
        o.dictionary = vec![("sotto".into(), "Sotto".into())];
        let out = process("send it to my addr sotto", &o);
        assert!(out.text.contains("221B Baker Street Sotto"));
    }

    #[test]
    fn commands_are_short_only() {
        assert_eq!(detect_command("scratch that"), Command::Undo);
        assert_eq!(detect_command("send it"), Command::Send);
        assert_eq!(
            detect_command("please send it to the team tomorrow"),
            Command::None
        );
    }

    #[test]
    fn formal_expands_contractions() {
        let out = process("i can't do it and it's fine", &opts(Style::Formal));
        assert_eq!(out.text, "I cannot do it and it is fine.");
    }

    #[test]
    fn very_casual_strips_sentence_marks() {
        let out = process("Hello there. This is fine!", &opts(Style::VeryCasual));
        assert_eq!(out.text, "hello there this is fine");
    }

    #[test]
    fn notes_bullets_sentences() {
        let out = process("buy milk. call mom. ship it.", &opts(Style::Notes));
        assert_eq!(out.text, "\u{2022} Buy milk.\n\u{2022} Call mom.\n\u{2022} Ship it.");
    }

    #[test]
    fn parses_compact_pairs() {
        let pairs = parse_pairs("a\u{1f}b\u{1e}c\u{1f}d");
        assert_eq!(pairs.len(), 2);
        assert_eq!(pairs[0], ("a".into(), "b".into()));
    }

    #[test]
    fn style_inference() {
        assert_eq!(Style::infer_for_package("com.google.android.gm"), Style::Formal);
        assert_eq!(Style::infer_for_package("com.whatsapp"), Style::Casual);
        assert_eq!(Style::infer_for_package("com.example.app"), Style::Default);
    }
}
