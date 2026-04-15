#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
验证Java文件的结构完整性，检查括号匹配等语法问题
"""
import sys
import re

def check_java_file(filepath):
    """检查Java文件的语法完整性"""
    print(f"检查文件: {filepath}")
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
        lines = content.split('\n')
    
    # 统计括号
    open_braces = content.count('{')
    close_braces = content.count('}')
    
    print(f"\n括号统计:")
    print(f"  开括号 {{: {open_braces}")
    print(f"  闭括号 }}: {close_braces}")
    print(f"  匹配状态: {'✓ 匹配' if open_braces == close_braces else '✗ 不匹配'}")
    
    # 检查类定义
    class_pattern = r'public\s+class\s+\w+'
    classes = re.findall(class_pattern, content)
    print(f"\n类定义:")
    for cls in classes:
        print(f"  {cls}")
    
    # 检查方法定义
    method_pattern = r'(public|private|protected|static)\s+\w+\s+\w+\s*\('
    methods = re.findall(method_pattern, content)
    print(f"\n方法定义数量: {len(methods)}")
    
    # 检查import语句
    import_count = len([line for line in lines if line.strip().startswith('import')])
    print(f"\nImport语句数量: {import_count}")
    
    # 检查文件结构
    print(f"\n文件结构:")
    print(f"  总行数: {len(lines)}")
    print(f"  文件开头: {lines[0].strip()[:50]}")
    print(f"  文件结尾: {lines[-1].strip()[:50]}")
    
    # 检查是否有未闭合的代码块
    brace_stack = []
    for i, line in enumerate(lines, 1):
        for j, char in enumerate(line):
            if char == '{':
                brace_stack.append((i, j))
            elif char == '}':
                if brace_stack:
                    brace_stack.pop()
                else:
                    print(f"\n✗ 错误: 第{i}行第{j}列有多余的闭括号")
    
    if brace_stack:
        print(f"\n✗ 错误: 有{len(brace_stack)}个未闭合的开括号:")
        for pos in brace_stack[:5]:  # 只显示前5个
            print(f"  第{pos[0]}行第{pos[1]}列")
    else:
        print(f"\n✓ 所有括号都正确匹配")
    
    return open_braces == close_braces and not brace_stack

if __name__ == "__main__":
    filepath = "D:/project/gaussdb-ticket-system/backend/src/main/java/com/example/gaussdb/service/impl/CoreServiceImpl.java"
    is_valid = check_java_file(filepath)
    
    if is_valid:
        print(f"\n✓✓✓ 文件结构完整，没有语法错误 ✓✓✓")
    else:
        print(f"\n✗✗✗ 文件有语法错误 ✗✗✗")
    
    sys.exit(0 if is_valid else 1)