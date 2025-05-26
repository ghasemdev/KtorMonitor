import re
import json
import sys

# Read from a file named github_releases.txt
with open('github_releases.txt', 'r', encoding='utf-8') as f:
    github_releases_content = f.read()

release_pattern = re.compile(
    r"^(?P<version>[\w.-]+)\s*\n"
    r"\s*(?P<date>\d{1,2}\s\w+\s\d{2}:\d{2})\s*\n"
    r"\s*@\w+.*?CosminMihuMDC.*?\n" # Author line
    r"(?P<rest_of_block>.*?)"
    r"(?=Assets\s\d+|\Z)", # End before "Assets X" or end of text
    re.MULTILINE | re.DOTALL
)

releases_data = []
matches = list(release_pattern.finditer(github_releases_content))

for match in matches:
    version = match.group("version").strip()
    date = match.group("date").strip()
    rest_of_block = match.group("rest_of_block")

    # Initial metadata patterns to identify lines that are NOT part of the description
    pre_description_metadata_patterns = [
        re.compile(r"^\s*\[\d+\]\s*$"),  # e.g., [88]
        re.compile(r"^\s*v" + re.escape(version) + r"\s*$"),  # e.g. v1.7.2
        re.compile(r"^\s*\[\d+\][a-f0-9]{7,40}\s*$"),      # e.g. [89]0943dca
        re.compile(r"^\s*Compare\s*$"),
        re.compile(r"^\s*Choose a tag to compare \(BUTTON\)\s*$"),
        re.compile(r"^\s*Could not load tags\s*$"),
        re.compile(r"^\s*Nothing to show\s*$"),
        re.compile(r"^\s*\[\d+\]\{\{ refName \}\}.*?$"), # e.g. [90]{{ refName }} default (*)
        re.compile(r"^\s*Loading\s*$"),
        re.compile(r"^\s*\[\d+\]View all tags\s*$"),
        # Line with the version string again, possibly with status like "Latest" or "Pre-release"
        # This is a key marker often appearing just BEFORE the actual description.
        re.compile(r"^\s*(\[\d+\]\s*)?" + re.escape(version) + r"(\s*(\[\d+\])?\s*(Latest|Pre-release))?\s*$"),
        re.compile(r"^\s*(Latest|Pre-release)\s*$"), # Status on its own line, also a marker
    ]
    
    # Patterns for lines that should be REMOVED if found anywhere in the description block
    removable_lines_patterns = [
        *pre_description_metadata_patterns, # Include all pre-description patterns for removal as well
        re.compile(r"^\s*Uh oh!\s*$"),
        re.compile(r"^\s*There was an error while loading\. \[.*?\]Please reload this page\.\s*$"),
        re.compile(r"^\s*All reactions\s*$"),
        re.compile(r"^\s*(\(\w+\sreact\)|.*?reacted with .*? emoji)\s*$", re.IGNORECASE),
        re.compile(r"^\s*\d+ people reacted\s*$"),
        re.compile(r"^\s*\*\s*🚀\s*\d+\s*reactions?\s*$"), # e.g. * 🚀 2 reactions
        re.compile(r"^\s*This commit was created on GitHub\.com and signed with GitHub’s verified signature\.\s*$"),
        re.compile(r"^\s*GPG key ID: [A-F0-9]+\s*$"),
        re.compile(r"^\s*Verified\s*$"),
        re.compile(r"^\s*\[\d+\]Learn about vigilant mode\.\s*$"),
        re.compile(r"^\s*____________________\s*$"),
        re.compile(r"^\s*(\[\d+\])?(Latest|Pre-release)\s*$"), # Handles "[XXX]Latest" or "Latest"
    ]


    description_lines_buffer = []
    lines_in_rest = rest_of_block.split('\n')
    start_collecting_description = False

    for line_content in lines_in_rest:
        stripped_line = line_content.strip()

        if not stripped_line: # Preserve blank lines if we've started collecting
            if start_collecting_description:
                description_lines_buffer.append(line_content)
            continue

        is_pre_desc_metadata = False
        for pattern in pre_description_metadata_patterns:
            if pattern.match(stripped_line):
                is_pre_desc_metadata = True
                break
        
        if not is_pre_desc_metadata:
            start_collecting_description = True
            description_lines_buffer.append(line_content)
        elif start_collecting_description:
            description_lines_buffer.append(line_content)

    temp_full_description = "\n".join(description_lines_buffer)
    
    final_cleaned_lines = []
    for line in temp_full_description.split('\n'):
        s_line = line.strip()
        
        is_removable = False
        if not s_line and not final_cleaned_lines: 
            continue
        if not s_line and final_cleaned_lines and not final_cleaned_lines[-1].strip(): 
            continue

        for pattern in removable_lines_patterns:
            if pattern.match(s_line):
                is_removable = True
                break
        
        if not is_removable:
            final_cleaned_lines.append(line) 

    description_text = "\n".join(final_cleaned_lines).strip()

    if not description_text.strip() and "Merge pull request" in rest_of_block:
        merge_pr_match = re.search(
            r"(Merge pull request #\d+ from .*?)(?:\n\n(.*?))?(?=\n\s*(Assets\s\d+|v\d+\.\d+\.\d+|[A-Z][a-z]+:))", 
            rest_of_block, 
            re.DOTALL
        )
        if merge_pr_match:
            title = merge_pr_match.group(1).strip()
            body = merge_pr_match.group(2).strip() if merge_pr_match.group(2) else ""
            
            cleaned_title = title
            cleaned_body = body
            
            extra_clean_patterns_for_merge = [
                re.compile(r"^\s*This commit was created on GitHub\.com and signed with GitHub’s verified signature\.\s*$", re.MULTILINE),
                re.compile(r"^\s*GPG key ID: [A-F0-9]+\s*$", re.MULTILINE),
                re.compile(r"^\s*Verified\s*$", re.MULTILINE),
                re.compile(r"^\s*\[\d+\]Learn about vigilant mode\.\s*$", re.MULTILINE),
                re.compile(r"^\s*____________________\s*$", re.MULTILINE),
                re.compile(r"^\s*(\[\d+\])?(Latest|Pre-release)\s*$", re.MULTILINE),
                re.compile(r"^\s*\[\d+\]\s*$", re.MULTILINE), 
            ]

            for pattern in extra_clean_patterns_for_merge:
                cleaned_title = pattern.sub("", cleaned_title).strip()
                cleaned_body = pattern.sub("", cleaned_body).strip()
            
            description_text = f"{cleaned_title}\n{cleaned_body}".strip() if cleaned_body else cleaned_title

    releases_data.append({
        "version": version,
        "date": date,
        "description": description_text
    })

print(json.dumps(releases_data, indent=2))
