#!/bin/bash

# Git hooks setup script
# Run this once after cloning the repository

set -e

echo "🔧 Setting up Git hooks for TDD enforcement..."

# Colors for output
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

# Configure Git to use the .githooks directory
git config core.hooksPath .githooks

# Make hooks executable
chmod +x "$SCRIPT_DIR/pre-commit"
chmod +x "$SCRIPT_DIR/pre-push"

echo ""
echo -e "${GREEN}✅ Git hooks installed successfully!${NC}"
echo ""
echo "The following hooks are now active:"
echo "  • pre-commit: Fast checks (compile, sensitive files, .block() usage)"
echo "  • pre-push: Full test suite + coverage gate (minimum 80%)"
echo ""
echo "To bypass hooks in emergencies, use: git commit --no-verify"
echo "                                     git push --no-verify"
echo ""
