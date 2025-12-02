<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Document</title>
    <style>
        body {
            background-color: rgba(0, 0, 0, 0.87);
        }
        #app {
            padding: 40px;
            width: 200px;
            text-shadow: 2px 2px 5px rgba(241, 241, 241, 0.5);
            color: white;
            margin: 50px;
        }
        li {
            margin-bottom: 10px;
        }
    </style>
</head>

<body>
<div id="app">
    <h1>${name}</h1>
    <ol>
        <#list oldNames as oldName>
            <li>${oldName}</li>
        </#list>
    </ol>
</div>

</body>

</html>