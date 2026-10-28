import sys
from pathlib import Path

PYTHON = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(PYTHON), str(PYTHON / "examples")]
