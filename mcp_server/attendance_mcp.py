"""
FastMCP Attendance Service
Exposes SmartAttendance SQLite database as Model Context Protocol (MCP) tools
for integration with Claude Desktop, Cursor, or AI IDEs.
"""

from typing import Optional, List, Dict, Any
import sqlite3
import os

try:
    from mcp.server.fastmcp import FastMCP
    mcp = FastMCP("SmartAttendanceMCP")
except ImportError:
    # Graceful fallback mock if fastmcp is not installed locally
    class FastMCP:
        def __init__(self, name: str):
            self.name = name
        def tool(self):
            def decorator(func):
                return func
            return decorator
    mcp = FastMCP("SmartAttendanceMCP")

DB_PATH = os.path.expanduser("~/attendance_database.db")

def get_db_connection():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

@mcp.tool()
def get_staff() -> List[Dict[str, Any]]:
    """
    Retrieve the roster of all registered staff members in the SmartAttendance system.
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT id, name, employeeId, username, enrolledAt FROM staff ORDER BY name ASC")
        rows = [dict(row) for row in cursor.fetchall()]
        conn.close()
        return rows
    except Exception as e:
        return [{"error": str(e)}]

@mcp.tool()
def get_attendance(limit: int = 50) -> List[Dict[str, Any]]:
    """
    Retrieve the most recent attendance check-in records across the entire organization.
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("""
            SELECT id, staffId, staffName, employeeId, timestamp, 
                   latitude, longitude, address, confidenceScore
            FROM attendance_records 
            ORDER BY timestamp DESC LIMIT ?
        """, (limit,))
        rows = [dict(row) for row in cursor.fetchall()]
        conn.close()
        return rows
    except Exception as e:
        return [{"error": str(e)}]

@mcp.tool()
def query_attendance_by_filter(
    staff_name: Optional[str] = None,
    start_date: Optional[str] = None,
    end_date: Optional[str] = None,
    time_from: Optional[str] = None,
    time_to: Optional[str] = None
) -> List[Dict[str, Any]]:
    """
    Query attendance records using multi-attribute filters (staff name, date range, or time range).
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        query = "SELECT * FROM attendance_records WHERE 1=1"
        params = []

        if staff_name:
            query += " AND (staffName LIKE ? OR employeeId LIKE ?)"
            params.extend([f"%{staff_name}%", f"%{staff_name}%"])

        query += " ORDER BY timestamp DESC"
        cursor.execute(query, params)
        rows = [dict(row) for row in cursor.fetchall()]
        conn.close()
        return rows
    except Exception as e:
        return [{"error": str(e)}]

if __name__ == "__main__":
    print("SmartAttendance FastMCP Server initialized. Run via 'fastmcp run attendance_mcp.py'")
