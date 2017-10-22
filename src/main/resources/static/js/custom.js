
function deleteToken(token){
    var result = window.confirm('Are you sure?');
    console.log("User says "+result+" for "+token);

    if (!result)
        return;

    // ugly piece of code below
    $.ajax({
        url: '/self/token/'+token,
        type: 'DELETE',
        success: function(result) {
            // Do something with the result
            window.location.reload();
        },
        error:function (xhr, ajaxOptions, thrownError){
            if (xhr.status == 200 || xhr.status == 404){
                window.location.reload();
            }else  if (xhr.status == 401 || xhr.status == 403){
                alert("Unauthorized attempt to delete the token");
            } else {
                console.log(xhr.status);
                console.log(xhr.responseText);
                alert("Unexpected error happened");
            }
            //alert(xhr.status);
            //alert(xhr.statusText);
            //alert(xhr.responseText);
        }
    });

}