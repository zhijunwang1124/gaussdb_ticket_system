# -*- coding: utf-8 -*-
"""生成 100 条工单测试 Excel。默认输出到 D:\\ticket_test_100.xlsx"""
import random
import sys
from datetime import datetime, timedelta

try:
    from openpyxl import Workbook
except ImportError:
    print("请先安装: pip install openpyxl")
    sys.exit(1)

OUTPUT = r"D:\ticket_test_100.xlsx"

PHASES = [
    "问题审核",
    "运维人员分析",
    "开发人员分析",
    "开发人员闭环",
    "运维人员闭环",
    "问题关闭",
]
SITES = ["北京局", "上海局", "深圳局", "杭州局", "成都局", "广州局"]
HANDLERS = ["张三", "李四", "王五", "赵六", "钱七", "孙八"]

HEADERS = [
    "工单号",
    "起始日期",
    "当前阶段",
    "局点",
    "当前处理人",
    "问题描述",
    "进展概述",
    "管控版本",
    "内核版本",
    "问题根因",
    "对外答复",
]


def main():
    wb = Workbook()
    ws = wb.active
    ws.append(HEADERS)
    base = datetime(2025, 1, 1)
    random.seed(42)
    for i in range(100):
        yw = "YW%d" % random.randint(1000000000, 9999999999)
        d = base + timedelta(days=random.randint(0, 420))
        ws.append(
            [
                yw,
                d.strftime("%Y-%m-%d"),
                random.choice(PHASES),
                random.choice(SITES),
                random.choice(HANDLERS),
                "问题描述样本%d：GaussDB 连接异常或查询超时相关描述" % (i + 1),
                "进展概述%d：已联系现场排查网络与参数" % (i + 1),
                "V%d.%d.%d" % (random.randint(1, 3), random.randint(0, 9), random.randint(0, 20)),
                "K%d.%d" % (random.randint(500, 599), random.randint(0, 9)),
                "问题根因%d：与配置/资源/网络相关的示例根因" % (i + 1),
                "对外答复%d：已处理并建议后续观察" % (i + 1),
            ]
        )
    wb.save(OUTPUT)
    print("已生成:", OUTPUT)


if __name__ == "__main__":
    main()
