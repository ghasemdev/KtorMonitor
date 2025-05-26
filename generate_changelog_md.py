import json
from datetime import datetime
import re

def format_date(date_str):
    """
    Converts date string like "25 May 17:23" to "YYYY-MM-DD".
    Uses a fixed year (2024) as per problem context, since input lacks explicit years.
    """
    try:
        dt_obj = datetime.strptime(f"{date_str} 2024", "%d %b %H:%M %Y")
        return dt_obj.strftime("%Y-%m-%d")
    except ValueError:
        return date_str # Fallback

def is_prerelease(version_str):
    """Checks if a version string indicates a pre-release."""
    return any(indicator in version_str.lower() for indicator in ['rc', 'alpha', 'beta', 'preview', 'm'])

def format_description(description_str, version):
    """
    Formats the description into markdown bullet points.
    Specifically cleans the known issue in version 1.3.0.
    """
    # Specific cleaning for version 1.3.0 - applied to the whole description string first.
    if version == "1.3.0":
        # Patterns to identify and remove unwanted multi-line text blocks
        unwanted_text_patterns = [
            re.compile(r"This commit was created on GitHub\.com and signed with GitHub’s verified signature\.(?:\s*GPG key ID: [A-F0-9]+)?(?:\s*Verified)?(?:\s*\[\d+\]Learn about vigilant mode\.?)?", re.IGNORECASE | re.DOTALL),
        ]
        for pattern in unwanted_text_patterns:
            description_str = pattern.sub("", description_str)
        description_str = description_str.strip() # Clean up any leading/trailing whitespace after removal


    if not description_str: # If description becomes empty after cleaning or was initially empty
        return "*No description provided.*"

    # General formatting for all descriptions:
    # Each non-empty line in the input description becomes a new bullet point.
    formatted_lines = []
    for line in description_str.split('\n'):
        stripped_line = line.strip()
        if stripped_line: # If the line has content after stripping whitespace
            # Remove existing common list markers (e.g., "* ", "- ", "1. ")
            cleaned_line = re.sub(r"^\s*[\*\-]\s*", "", stripped_line)
            cleaned_line = re.sub(r"^\s*\d+\.\s*", "", cleaned_line)
            if cleaned_line: # Ensure line is not empty after stripping markers
                formatted_lines.append(f"* {cleaned_line}") 
    
    if not formatted_lines: # If all lines were empty or whitespace only
        return "*No description provided.*"

    return "\n".join(formatted_lines)


def generate_changelog_markdown(releases_data):
    markdown_output = ["# Changelog\n"]
    markdown_output.append("All notable changes to this project will be documented in this file.")
    markdown_output.append("The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).\n")

    for release in releases_data: # Assumes releases.json is newest first
        version = release.get("version", "N/A")
        date_str = release.get("date", "")
        description = release.get("description", "").strip() 

        formatted_date = format_date(date_str)
        
        heading = f"## [{version}] - {formatted_date}"
        if is_prerelease(version):
            heading += " (Pre-release)"
        
        markdown_output.append(heading)
        
        formatted_desc = format_description(description, version)
        markdown_output.append(formatted_desc)
            
        markdown_output.append("\n") 

    return "\n".join(markdown_output)

if __name__ == "__main__":
    try:
        with open("releases.json", "r", encoding="utf-8") as f:
            releases = json.load(f) 
        
        markdown_content = generate_changelog_markdown(releases)
        print(markdown_content)

    except FileNotFoundError:
        print("Error: The file 'releases.json' was not found.", file=sys.stderr)
    except json.JSONDecodeError:
        print("Error: Failed to decode 'releases.json'. Ensure it's valid JSON.", file=sys.stderr)
    except Exception as e:
        print(f"An unexpected error occurred: {e}", file=sys.stderr)
