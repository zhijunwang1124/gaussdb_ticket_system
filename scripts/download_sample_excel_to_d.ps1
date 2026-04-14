# 启动后端后执行，将 100 条样例工单 Excel 保存到 D:\ticket_test_100.xlsx
$uri = "http://localhost:8080/api/sample/tickets-excel-100"
$out = "D:\ticket_test_100.xlsx"
Invoke-WebRequest -Uri $uri -OutFile $out -UseBasicParsing
Write-Host "已保存: $out"
